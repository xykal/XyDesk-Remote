import test, { beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { RevisionedHistoryWindow, chatStorageFailure, chatStorageFailureEvent } from './chat-storage-policy.mjs';
import worker, { GlobalChat } from './worker.mjs';
const originalFetch = globalThis.fetch;
beforeEach(() => { globalThis.fetch = async () => new Response(JSON.stringify({id:42,login:'alice'})); });
afterEach(() => { globalThis.fetch = originalFetch; });

test('simultaneous polls coalesce and expire after a bounded window', async () => {
  let now=0, reads=0; const cache=new RevisionedHistoryWindow({now:()=>now,ttlMs:5});
  const load=async()=>{reads++; return [{id:'a'}];};
  const rows=await Promise.all([cache.load(load),cache.load(load),cache.load(load)]);
  assert.equal(reads,1); assert.equal(rows[0],rows[1]);
  now=6; await cache.load(load); assert.equal(reads,2);
});
test('in-flight old snapshot cannot overwrite a post-mutation revision', async () => {
  const cache=new RevisionedHistoryWindow(); let resolve;
  const old=cache.load(()=>new Promise(r=>{resolve=r;})); await Promise.resolve();
  cache.invalidate(); const current=await cache.load(async()=>[{revision:2}]);
  resolve([{revision:1}]); await old;
  assert.equal((await cache.load(()=>assert.fail('unexpected reread')))[0].revision,2);
  assert.equal(current[0].revision,2);
});
test('failed reads are not cached and quota errors have no raw/private details', async () => {
  const cache=new RevisionedHistoryWindow();
  await assert.rejects(cache.load(()=>{throw Error('quota');}));
  assert.deepEqual(await cache.load(async()=>[]),[]);
  const response=chatStorageFailure(Error('Exceeded allowed rows read in Durable Objects free tier. private-data'));
  assert.equal(response.status,503); assert.equal(response.headers.get('retry-after'),'300');
  const body=await response.json(); assert.equal(body.code,'chat_storage_quota_exceeded');
  assert.equal(JSON.stringify(body).includes('private-data'),false);
});
test('history quota failure is a structured 503, never a fake empty success', async () => {
  const env={GLOBAL_CHAT:{idFromName:()=>1,get:()=>({fetch:async()=>{throw Error('Exceeded allowed rows read in Durable Objects free tier.');}})}};
  const response=await worker.fetch(new Request('https://app.test/api/chat/history'),env);
  assert.equal(response.status,503); assert.equal((await response.json()).code,'chat_storage_quota_exceeded');
});
test('unauthenticated mutations never touch a quota-exhausted object', async () => {
  const env={GLOBAL_CHAT:{idFromName:()=>assert.fail('must authenticate before DO access')}};
  const response=await worker.fetch(new Request('https://app.test/api/chat/delete',{method:'POST',body:'{}'}),env);
  assert.equal(response.status,401);
});
test('legacy object capabilities cannot authorize an old destructive delete', async () => {
  let calls=0;
  const env={GLOBAL_CHAT:{idFromName:()=>1,get:()=>({fetch:async request=>{
    calls++; assert.equal(new URL(request.url).pathname,'/mutation-capabilities'); return new Response('old',{status:404});
  }})}};
  const response=await worker.fetch(new Request('https://app.test/api/chat/delete',{method:'POST',headers:{authorization:'Bearer test'},body:'{}'}),env);
  assert.equal(response.status,503); assert.equal((await response.json()).code,'chat_migration_pending'); assert.equal(calls,1);
});
test('shared history snapshots apply joined-at privacy separately per request', async () => {
  let reads=0;
  const storage={list:async options=>{reads++;assert.equal(options.limit,100);assert.equal(options.reverse,true);return new Map([
    ['msg:a',{id:'a',name:'Alice',text:'one',at:10}],['msg:b',{id:'b',name:'Alice',text:'two',at:20}],
  ]);}};
  const chat=new GlobalChat({storage},{});
  assert.deepEqual((await chat.loadHistory(15)).map(x=>x.id),['b']);
  assert.deepEqual((await chat.loadHistory(0)).map(x=>x.id),['a','b']);
  assert.equal(reads,1);
});
test('join lookups cache separately per identity and remain bounded', async () => {
  let reads=0;const data=new Map([['join:cid:a',10],['join:cid:b',20]]);
  const chat=new GlobalChat({storage:{get:async k=>{reads++;return data.get(k);},put:async(k,v)=>data.set(k,v)}},{});
  assert.equal(await chat.resolveJoinedAt(new URL('https://x/history?client_id=a')),10);
  assert.equal(await chat.resolveJoinedAt(new URL('https://x/history?client_id=a&joined_at=15')),15);
  assert.equal(await chat.resolveJoinedAt(new URL('https://x/history?client_id=b')),20);assert.equal(reads,2);
  for(let i=0;i<1001;i++)await chat.resolveJoinedAt(new URL('https://x/history?client_id=c'+i));
  assert.equal(chat.joinedCache.size,1000);
});

test('WebSocket history failures are explicit, sanitized, never empty success', async () => {
  const event=await chatStorageFailureEvent(Error('Exceeded allowed rows read in Durable Objects free tier. private-payload'));
  assert.equal(event.type,'history_unavailable'); assert.equal(event.http_status,503);
  assert.equal(event.code,'chat_storage_quota_exceeded'); assert.equal(event.retry_after,'300');
  assert.equal('messages' in event,false); assert.equal(JSON.stringify(event).includes('private-payload'),false);
});
test('non-quota WebSocket history failure is not mislabeled quota or successful history', async () => {
  const event=await chatStorageFailureEvent(Error('internal-private-exception'));
  assert.equal(event.type,'history_unavailable'); assert.equal(event.code,'chat_temporarily_unavailable');
  assert.equal(event.status,'error'); assert.equal(JSON.stringify(event).includes('internal-private'),false);
});

test('pinned reads coalesce across callers and do not scan on every history request', async () => {
  let reads=0;
  const chat=new GlobalChat({storage:{get:async key=>{assert.equal(key,'pinned_message');reads++;return {id:'pin1'};}}},{});
  const results=await Promise.all(Array.from({length:40},()=>chat.loadPinnedMessage()));
  assert.equal(reads,1); assert.equal(results.every(x=>x.id==='pin1'),true);
  chat.pinnedWindow.invalidate(); await chat.loadPinnedMessage(); assert.equal(reads,2);
});
test('pinned read failures propagate and are not fabricated as empty/unpinned success', async () => {
  let fail=true;
  const chat=new GlobalChat({storage:{get:async()=>{if(fail)throw Error('Exceeded allowed rows read');return null;}}},{});
  await assert.rejects(chat.loadPinnedMessage());fail=false;
  assert.equal(await chat.loadPinnedMessage(),null);
});
test('old in-flight pin cannot refill the cache after unpin invalidation', async () => {
  let resolve, calls=0;
  const chat=new GlobalChat({storage:{get:async()=>{calls++;if(calls===1)return await new Promise(r=>{resolve=r;});return null;}}},{});
  const old=chat.loadPinnedMessage(); await Promise.resolve();await Promise.resolve();
  chat.pinnedWindow.invalidate(); assert.equal(await chat.loadPinnedMessage(),null);
  resolve({id:'deleted-pin'}); await old;
  assert.equal(await chat.loadPinnedMessage(),null); assert.equal(calls,2);
});
