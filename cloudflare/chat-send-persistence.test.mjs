import test, { beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { persistChatSend, pruneChatSendReceipts, validateDurableSend, SEND_REPLAY_MAX_AGE_MS } from './chat-send-persistence.mjs';
import worker, { GlobalChat } from './worker.mjs';
const NOW = 1_800_000_000_000;
const principal = { id:42, login:'alice' };
const nonce = 'local-519f0398-4c72-49c2-ae83-912056627f83';
const payload = (extra={}) => ({outbox_v:1, outbox_author_id:42, outbox_created_at:NOW,
  client_msg_id:nonce, name:'Alice', github_login:'alice', text:'original', country:'ID', ...extra});
const row = (extra={}) => ({id:'canonical-1', author_id:42, github_login:'alice', text:'original', at:NOW, ...extra});
function fixture() {
  let data = new Map(), tail = Promise.resolve(), failPut = 0;
  const api = map => ({
    get:async key => structuredClone(map.get(key)),
    put:async (key,value) => { if (failPut && --failPut === 0) throw Error('disk write failed'); map.set(key, structuredClone(value)); },
    delete:async key => map.delete(key),
    list:async ({prefix='',end,limit=1000}={}) => new Map([...map].filter(([k]) => k.startsWith(prefix) && (!end || k < end))
      .sort(([a],[b]) => a.localeCompare(b)).slice(0,limit)),
  });
  const storage = {
    get:(...args) => api(data).get(...args), put:(...args) => api(data).put(...args),
    delete:(...args) => api(data).delete(...args), list:(...args) => api(data).list(...args),
    transaction(fn) {
      const operation = tail.then(async () => {
        const next = structuredClone(data), result = await fn(api(next)); data = next; return result;
      });
      tail = operation.catch(() => {}); return operation;
    },
  };
  return { storage, entries:() => [...data], failAtPut:n => {failPut=n;} };
}
const savedRows = f => f.entries().filter(([key]) => key.startsWith('msg:'));
const send = (f, body=payload(), message=row(), who=principal, now=NOW) => persistChatSend(f.storage,message,who,body,now);
test('authenticated retries return one canonical message and one author-bound receipt', async () => {
  const f=fixture(), first=await send(f), retry=await send(f,payload(),row({id:'must-not-exist'}));
  assert.equal(first.inserted,true); assert.equal(retry.inserted,false);
  assert.equal(retry.message.id,'canonical-1'); assert.equal(retry.receipt.client_msg_id,nonce);
  assert.equal(retry.receipt.author_id,42); assert.equal(savedRows(f).length,1);
  assert.ok(f.entries().filter(([k]) => k.startsWith('send-')).every(([,v]) => !JSON.stringify(v).includes('original')));
});
test('concurrent HTTP/WS attempts with the same immutable intent insert only once', async () => {
  const f=fixture(); const results=await Promise.all(Array.from({length:12},(_,i) => send(f,payload(),row({id:'message-'+i}))));
  assert.equal(results.filter(r => r.inserted).length,1); assert.equal(savedRows(f).length,1);
  assert.equal(new Set(results.map(r => r.receipt.id)).size,1);
});
test('the same nonce used by different verified provider IDs cannot shadow messages', async () => {
  const f=fixture(); await send(f);
  const other=await send(f,payload({outbox_author_id:99,github_login:'bob'}),row({id:'bob-1',author_id:99,github_login:'bob'}),{id:99,login:'bob'});
  assert.equal(other.inserted,true); assert.equal(savedRows(f).length,2);
});
test('changed text, media, view-once, reply, envelope or creation time conflicts without inserting', async () => {
  const f=fixture(); await send(f);
  for (const extra of [{text:'other'},{attachment_data:'media'},{view_once:true},{reply_to_id:'other'},
    {e2ee_cipher:'cipher'},{outbox_created_at:NOW-1}]) {
    assert.equal((await send(f,payload(extra),row({id:'must-not-exist'}))).status,409);
  }
  assert.equal(savedRows(f).length,1);
});
test('derived translation, transport type and verified username changes do not change intent', async () => {
  const f=fixture(); await send(f);
  const retry=await send(f,payload({type:'msg',translated:'derived',github_login:'alice-renamed'}),
    row({id:'must-not-exist',github_login:'alice-renamed'}),{id:42,login:'alice-renamed'});
  assert.equal(retry.inserted,false); assert.equal(savedRows(f).length,1);
});
test('a retry returns the edited CURRENT row, not the original queued text', async () => {
  const f=fixture(); await send(f); await f.storage.put('msg:canonical-1',row({text:'edited',revision:1}));
  const retry=await send(f); assert.equal(retry.message.text,'edited'); assert.equal(retry.message.revision,1);
});
test('a retry returns a deletion tombstone without resurrecting queued content', async () => {
  const f=fixture(); await send(f);
  await f.storage.put('msg:canonical-1',row({text:'Pesan ini dihapus',deleted:true,revision:1}));
  const retry=await send(f); assert.equal(retry.message.deleted,true); assert.equal(retry.message.text,'Pesan ini dihapus');
});
test('trimmed history produces receipt-only ACK, never reconstructed stale content', async () => {
  const f=fixture(); await send(f); await f.storage.delete('msg:canonical-1');
  const retry=await send(f); assert.equal(retry.message,null); assert.equal(retry.receipt_only,true);
  assert.equal(retry.receipt.id,'canonical-1'); assert.equal(savedRows(f).length,0);
});
test('receipt/expiry failure rolls back the message and permits a clean retry', async () => {
  for (const failure of [2,3]) {
    const f=fixture(); f.failAtPut(failure); await assert.rejects(send(f),/disk write failed/);
    assert.equal(f.entries().length,0); assert.equal((await send(f)).inserted,true);
  }
});
test('durable sends fail closed for guests, switched accounts, malformed IDs and unsupported protocol', async () => {
  const f=fixture();
  for (const [body,who,status] of [[payload(),null,401],[payload({outbox_author_id:99}),principal,409],
    [payload({client_msg_id:'caller-selected-public-id'}),principal,400],[payload({outbox_v:2}),principal,400]]) {
    assert.equal((await send(f,body,row(),who)).status,status);
  }
  assert.equal(f.entries().length,0);
});
test('server-created row cannot be bound to a different principal', async () => {
  const f=fixture(); assert.equal((await send(f,payload(),row({author_id:99}))).status,401);
  assert.equal(f.entries().length,0);
});
test('expired and invalid clock values never touch storage; GC cannot enable old replay', async () => {
  const f=fixture(); await send(f);
  const later=NOW+SEND_REPLAY_MAX_AGE_MS;
  assert.equal((await send(f,payload(),row(),principal,later)).code,'outbox_expired');
  await pruneChatSendReceipts(f.storage,later);
  assert.equal(f.entries().filter(([k]) => k.startsWith('send-')).length,0);
  assert.equal((await send(f,payload(),row(),principal,later)).code,'outbox_expired');
  for (const time of [null,'123',NOW+300_001,Infinity])
    assert.equal(validateDurableSend(principal,payload({outbox_created_at:time}),NOW).code,'invalid_outbox_time');
  assert.equal(savedRows(f).length,1);
});
test('GC is bounded and preserves unexpired receipts and canonical history', async () => {
  const f=fixture();
  for (let i=0;i<3;i++) await send(f,payload({client_msg_id:`local-519f0398-4c72-49c2-ae83-912056627f8${i}`}),row({id:'canonical-'+i}));
  assert.equal(await pruneChatSendReceipts(f.storage,NOW,1),0);
  assert.equal(await pruneChatSendReceipts(f.storage,NOW+SEND_REPLAY_MAX_AGE_MS,1),1);
  assert.equal(f.entries().filter(([k]) => k.startsWith('send-receipt:')).length,2);
  assert.equal(savedRows(f).length,3);
});
const originalFetch=globalThis.fetch;
beforeEach(() => { globalThis.fetch=async url => String(url)==='https://api.github.com/user'
  ? new Response(JSON.stringify(principal),{status:200}) : new Response('offline',{status:503}); });
afterEach(() => { globalThis.fetch=originalFetch; });
function httpRequest(body, token='outbox-test-token') {
  return new Request('https://chat.test/send',{method:'POST',headers:{'content-type':'application/json',
    ...(token ? {authorization:'Bearer '+token} : {})},body:JSON.stringify(body)});
}
function liveFixture() {
  const f=fixture(), chat=new GlobalChat({storage:f.storage},{}), broadcast=[], pushes=[];
  chat.broadcast=(_,event) => broadcast.push(JSON.parse(event)); chat.trimHistory=async () => {};
  chat.pushChatNotification=async msg => pushes.push(msg);
  return {...f,chat,broadcast,pushes};
}
test('HTTP followed by WS retries ACK privately, with only one broadcast and push', async () => {
  const f=liveFixture(), body=payload({outbox_created_at:Date.now()});
  const response=await f.chat.fetch(httpRequest(body)); assert.equal(response.status,200);
  const first=await response.json(), acks=[];
  assert.notEqual(first.message.id,nonce); assert.match(first.message.id,/^[a-z0-9]{9}-[0-9a-f-]{36}$/);
  const session={principal,name:'Alice',country:'ID',cfCountry:'ID',windowAt:Date.now(),sent:0,
    ws:{send:event => acks.push(JSON.parse(event))}};
  await f.chat.onMessage(session,{data:JSON.stringify({...body,type:'msg'})});
  assert.equal(savedRows(f).length,1); assert.equal(f.broadcast.length,1); assert.equal(f.pushes.length,1);
  assert.equal(acks.at(-1).type,'send_ack'); assert.equal(acks.at(-1).inserted,false);
  assert.equal(acks.at(-1).receipt.id,first.message.id);
});
test('HTTP durable guest rejection is explicit and cannot persist a forged founder', async () => {
  const f=liveFixture(), response=await f.chat.fetch(httpRequest(payload({github_login:'xykal',outbox_created_at:Date.now()}),''));
  assert.equal(response.status,401); assert.equal(savedRows(f).length,0); assert.equal(f.broadcast.length,0);
});
test('HTTP storage quota failure is 503 with retry guidance, not false acceptance', async () => {
  const f=liveFixture(); f.storage.transaction=async () => {throw Error('exceeded allowed rows written');};
  const response=await f.chat.fetch(httpRequest(payload({outbox_created_at:Date.now()})));
  assert.equal(response.status,503); assert.equal(response.headers.get('retry-after'),'300');
  assert.equal(f.broadcast.length,0); assert.equal(f.pushes.length,0);
});
test('capability negotiation requires authentication and does not read SQL/history', async () => {
  const storage=new Proxy({},{get:() => {throw Error('capabilities must not touch storage');}});
  const chat=new GlobalChat({storage},{});
  assert.equal((await chat.fetch(new Request('https://chat.test/send-capabilities'))).status,401);
  const response=await chat.fetch(new Request('https://chat.test/send-capabilities',{headers:{authorization:'Bearer outbox-test-token'}}));
  const body=await response.json(); assert.equal(response.status,200); assert.equal(body.author_id,42);
  assert.equal(body.send_protocol,1); assert.equal(body.replay_max_age_ms,SEND_REPLAY_MAX_AGE_MS);
});
test('edge forwards capability auth to the ORIGINAL global object, never a new namespace', async () => {
  let forwarded;
  const response=await worker.fetch(new Request('https://chat.test/api/chat/send-capabilities',{headers:{authorization:'Bearer probe'}}),{
    GLOBAL_CHAT:{idFromName:name => {assert.equal(name,'xydesk-global-chat-v1'); return 'original';},get:id => {
      assert.equal(id,'original'); return {fetch:async req => {forwarded=req;return new Response('{}');}};
    }},
  });
  assert.equal(response.status,200); assert.equal(new URL(forwarded.url).pathname,'/send-capabilities');
  assert.equal(forwarded.headers.get('authorization'),'Bearer probe');
});

test('WS storage failure cannot emit an acceptance ACK or broadcast', async () => {
  const f=liveFixture(), events=[];
  f.storage.transaction=async () => {throw Error('exceeded allowed rows written');};
  await f.chat.onMessage({principal,name:'Alice',country:'ID',cfCountry:'ID',sent:0,windowAt:Date.now(),
    ws:{send:event => events.push(JSON.parse(event))}},
    {data:JSON.stringify({...payload({outbox_created_at:Date.now()}),type:'msg'})});
  assert.equal(events.at(-1).type,'history_unavailable');
  assert.equal(events.at(-1).client_msg_id,nonce); assert.equal(events.at(-1).http_status,503);
  assert.equal(f.broadcast.length,0); assert.equal(f.pushes.length,0);
});
test('stale expiry index cannot delete a replacement receipt with a later expiry', async () => {
  const f=fixture(); await send(f);
  const [key,value]=f.entries().find(([k]) => k.startsWith('send-receipt:'));
  await f.storage.put(key,{...value,expires_at:NOW+2*SEND_REPLAY_MAX_AGE_MS});
  await pruneChatSendReceipts(f.storage,NOW+SEND_REPLAY_MAX_AGE_MS);
  assert.ok(await f.storage.get(key)); assert.equal(savedRows(f).length,1);
});
