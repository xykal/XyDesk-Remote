import test, { beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { applyChatMutation } from './chat-mutations.mjs';
import worker, { GlobalChat } from './worker.mjs';
const originalFetch = globalThis.fetch;
beforeEach(() => { globalThis.fetch = async url => String(url) === 'https://api.github.com/user'
  ? new Response(JSON.stringify({ id: 42, login: 'alice' }), { status: 200 })
  : new Response('offline', { status: 503 }); });
afterEach(() => { globalThis.fetch = originalFetch; });
const row = () => ({ id: 'message-1', author_id: 42, github_login: 'alice', name: 'Alice', text: 'original', at: 12, revision: 0 });
const body = (extra = {}) => ({ id: 'message-1', expected_revision: 0, operation_id: 'operation-123456789', text: 'updated', ...extra });
function fixture(initial = row()) {
  const data = new Map([['msg:message-1', initial]]);
  let tail = Promise.resolve();
  const storage = {
    get: async k => data.get(k), put: async (k,v) => data.set(k,v), delete: async k => data.delete(k),
    list: async ({prefix}) => new Map([...data].filter(([k]) => k.startsWith(prefix))),
    transaction(fn) { const op = tail.then(() => fn(storage)); tail = op.catch(() => {}); return op; },
  };
  const chat = new GlobalChat({storage}, {});
  return {chat, data, storage};
}
function request(action, payload = body(), token = 'test-token') {
  return new Request('https://chat.test/' + action, { method:'POST',
    headers: {'content-type':'application/json', ...(token ? {'authorization': 'Bearer '+token} : {})}, body:JSON.stringify(payload) });
}
test('delete retains stable tombstone identity/order and strips all content/envelopes/media', () => {
  const result = applyChatMutation({...row(), attachment_data:'SECRET', e2ee_cipher:'SECRET', reply_to_text:'SECRET'}, {id:42}, body(), 'delete', 50);
  assert.equal(result.status,200); assert.equal(result.message.id,'message-1'); assert.equal(result.message.at,12);
  assert.equal(result.message.deleted,true); assert.equal(result.message.revision,1);
  assert.equal(result.message.text,'Pesan ini dihapus'); assert.ok(!JSON.stringify(result).includes('SECRET'));
});
test('forged nickname/login and legacy identity cannot authorize a mutation', () => {
  assert.equal(applyChatMutation(row(),{id:99},body({github_login:'alice'}),'edit',50).status,403);
  assert.equal(applyChatMutation({...row(),author_id:null},{id:42},body(),'delete',50).status,403);
});
test('edit clears obsolete translation and envelope without fabricating verification', () => {
  const result=applyChatMutation({...row(),translated:'old',e2ee_cipher:'old',e2ee_sig:'old'},{id:42},body(),'edit',50);
  assert.equal(result.message.text,'updated'); assert.equal(result.message.edited_at,50);
  assert.equal(result.message.e2ee_sig,''); assert.equal(result.message.translated,''); assert.equal(result.message.e2ee_verified,false);
});
test('idempotent retry is payload bound and stale revisions cannot overwrite', () => {
  const first=applyChatMutation(row(),{id:42},body(),'edit',50).message;
  assert.equal(applyChatMutation(first,{id:42},body(),'edit',60).message.revision,1);
  assert.equal(applyChatMutation(first,{id:42},body({text:'different'}),'edit',60).status,409);
  assert.equal(applyChatMutation(first,{id:42},body({operation_id:'different-operation'}),'delete',60).status,409);
});
test('deleted messages cannot be edited/resurrected; invalid/oversized/media edits fail', () => {
  const deleted=applyChatMutation(row(),{id:42},body(),'delete',50).message;
  assert.equal(applyChatMutation(deleted,{id:42},body({expected_revision:1,operation_id:'different-operation'}),'edit',60).status,409);
  for(const text of ['', 'x'.repeat(501)]) assert.equal(applyChatMutation(row(),{id:42},body({text}),'edit',50).status,400);
  assert.equal(applyChatMutation({...row(),attachment_type:'image'},{id:42},body(),'edit',50).status,400);
});
test('HTTP mutations require a real provider identity, not body claims', async () => {
  const {chat,data}=fixture();
  assert.equal((await chat.fetch(request('delete',body(),''))).status,401);
  globalThis.fetch=async()=>new Response(JSON.stringify({id:99,login:'mallory'}));
  assert.equal((await chat.fetch(request('delete'))).status,403);
  assert.equal(data.get('msg:message-1').text,'original');
});
test('delete is persisted, broadcasts canonical row, scrubs quotes and clears pin', async () => {
  const {chat,data}=fixture(); data.set('pinned_message',{id:'message-1',text:'original'});
  data.set('msg:reply',{...row(),id:'reply',reply_to_id:'message-1',reply_to_text:'original'});
  const events=[]; chat.sessions.add({ws:{send:text=>events.push(JSON.parse(text))}});
  const response=await chat.fetch(request('delete')); assert.equal(response.status,200);
  assert.equal((await response.json()).message.deleted,true); assert.equal(data.get('pinned_message'),undefined);
  assert.equal(data.get('msg:reply').reply_to_text,'Pesan ini dihapus');
  assert.equal(events.find(e=>e.type==='mutation').message.deleted,true);
  const history=await chat.loadHistory(); assert.equal(history.find(m=>m.id==='message-1').deleted,true);
});
test('concurrent HTTP edits with the same revision have only one winner', async () => {
  const {chat}=fixture(); const result=await Promise.all([
    chat.fetch(request('edit')), chat.fetch(request('edit',body({operation_id:'other-operation-123',text:'other'}))),
  ]); assert.deepEqual(result.map(r=>r.status).sort(),[200,409]);
});
test('old unauthenticated WebSocket delete route cannot mutate storage', async () => {
  const {chat,data}=fixture(); const events=[];
  await chat.onMessage({ws:{send:t=>events.push(JSON.parse(t))}}, {data:JSON.stringify({type:'delete',id:'message-1'})});
  assert.equal(data.get('msg:message-1').text,'original'); assert.equal(events[0].code,'authenticated_http_required');
});
test('authenticated send stamps provider ID, never a body-supplied author ID', async () => {
  const {chat}=fixture(); const response=await chat.fetch(request('send', {name:'Alice',github_login:'alice',author_id:99,text:'hello'}));
  assert.equal(response.status,200); const json=await response.json(); assert.equal(json.message.author_id,42);
  assert.equal(json.message.github_login,'alice');
  assert.equal((await chat.fetch(request('send',{name:'Bob',github_login:'bob',text:'wrong-account'}))).status,401);
});
test('edge mutation route forwards Authorization and never fakes success if unavailable', async () => {
  assert.equal((await worker.fetch(new Request('https://app.test/api/chat/edit',request('edit')),{})).status,503);
  const {chat}=fixture(); const env={GLOBAL_CHAT:{idFromName:n=>n,get:()=>({fetch:r=>chat.fetch(r)})}};
  const response=await worker.fetch(new Request('https://app.test/api/chat/edit',request('edit')),env);
  assert.equal(response.status,200); assert.equal((await response.json()).message.text,'updated');
});

test('stale pin requests cannot republish deleted plaintext', async () => {
  const {chat,data}=fixture();
  await chat.fetch(request('delete'));
  const stalePin={action:'pin',message:{id:'message-1',text:'original'}};
  assert.equal((await chat.fetch(request('pin',stalePin))).status,409);
  await chat.onMessage({ws:{send:()=>{}}},{data:JSON.stringify({type:'pin',...stalePin})});
  assert.equal(data.get('pinned_message'),undefined);
});

test('post-commit pin read failure cannot turn a committed mutation into an apparent failure', async () => {
  const {chat, storage, data}=fixture();let committed=false;
  const rawTransaction=storage.transaction.bind(storage), rawGet=storage.get;
  storage.transaction=async fn=>{const result=await rawTransaction(fn);committed=true;return result;};
  storage.get=async key=>{if(committed)throw Error('Exceeded allowed rows read');return rawGet(key);};
  const events=[];chat.sessions.add({ws:{send:t=>events.push(JSON.parse(t))}});
  const response=await chat.fetch(request('delete'));
  assert.equal(response.status,200);assert.equal(data.get('msg:message-1').deleted,true);
  assert.deepEqual(events.find(x=>x.type==='pin'),{type:'pin',pinned_message:null});
});
