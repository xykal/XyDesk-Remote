import test, {beforeEach,afterEach} from 'node:test';
import assert from 'node:assert/strict';
import {OfficialChannels,channelRole,FOUNDER_GITHUB_ID} from './official-channels.mjs';
import worker,{GlobalChat} from './worker.mjs';
const saved=globalThis.fetch;
let user;
beforeEach(()=>{user={id:99,login:'reader'};globalThis.fetch=async()=>new Response(JSON.stringify(user));});
afterEach(()=>{globalThis.fetch=saved;});
function fixture(env={}) {
 const data=new Map();let reads=0,tail=Promise.resolve();
 const storage={get:async k=>{reads++;return data.get(k);},put:async(k,v)=>data.set(k,v),
 list:async({prefix,end,limit,reverse})=>{reads++;let a=[...data].filter(([k])=>k.startsWith(prefix)&&(!end||k<end)).sort(([a],[b])=>a.localeCompare(b));if(reverse)a.reverse();return new Map(a.slice(0,limit));},
 transaction(fn){const p=tail.then(()=>fn(storage));tail=p.catch(()=>{});return p;}};
 return {channel:new OfficialChannels(storage,env),data,storage,reads:()=>reads};
}
const nonce='ad362678-9fbf-4d11-8a3d-539d44ef9951';
function req(method='GET',path='/channels',body,token) {return new Request('https://test'+path,{method,headers:{...(token?{authorization:'Bearer '+token}:{}),'content-type':'application/json'},...(body!==undefined?{body:JSON.stringify(body)}:{})});}
const post=(text='Welcome #PanduanPengguna')=>({text,client_post_id:nonce,role:'founder',github_login:'xykal',author_id:FOUNDER_GITHUB_ID});
test('roles trust numeric provider IDs, never login/name/client role or string IDs',()=>{
 assert.equal(channelRole({id:99,login:'xykal',role:'founder'}),null);
 assert.equal(channelRole({id:FOUNDER_GITHUB_ID,login:'renamed'}),'founder');
 assert.equal(channelRole({id:99},{CHAT_CHANNEL_ROLE_IDS:'{"team":[99]}'}),'team');
 assert.equal(channelRole({id:99},{CHAT_CHANNEL_ROLE_IDS:'{"admin":["99"]}'}),null);
 assert.equal(channelRole({id:99},{CHAT_CHANNEL_ROLE_IDS:'bad-json'}),null);
});
test('anonymous catalogue does not require storage and cannot post',async()=>{
 const f=fixture();const j=await(await f.channel.fetch(req())).json();assert.equal(j.channels.length,3);assert.equal(j.can_post,false);assert.equal(f.reads(),0);
 assert.equal((await f.channel.fetch(req('POST','/channels/announcement/posts',post()))).status,401);assert.equal(f.data.size,0);
});
test('forged founder/admin cannot write through real GlobalChat route',async()=>{
 const f=fixture();const chat=new GlobalChat({storage:f.storage},{});
 const r=await chat.fetch(req('POST','/channels/announcement/posts',post(),'reader-token'));
 assert.equal(r.status,403);assert.equal(f.reads(),0);assert.equal(f.data.size,0);
});
test('founder canonical identity is provider-issued, duplicate retry creates exactly one post',async()=>{
 user={id:FOUNDER_GITHUB_ID,login:'xykal'};const f=fixture();
 const [a,b]=await Promise.all([f.channel.fetch(req('POST','/channels/announcement/posts',post(),'owner')),f.channel.fetch(req('POST','/channels/announcement/posts',post(),'owner'))]);
 assert.deepEqual([a.status,b.status],[201,200]);const j=await a.json();assert.equal(j.post.author_role,'founder');assert.equal(j.post.github_login,'xykal');
 assert.equal([...f.data.keys()].filter(x=>x.includes(':post:')).length,1);
 assert.equal((await f.channel.fetch(req('POST','/channels/announcement/posts',post('changed'),'owner'))).status,409);
});
test('configured team/admin may post; absent configuration and removed role fail closed',async()=>{
 for(const role of ['team','admin']){const f=fixture({CHAT_CHANNEL_ROLE_IDS:JSON.stringify({[role]:[99]})});assert.equal((await f.channel.fetch(req('POST','/channels/informasi/posts',post(),'team'))).status,201);f.channel.env={};assert.equal((await f.channel.fetch(req('POST','/channels/informasi/posts',post(),'team'))).status,403);}
});
test('unknown channel, cross-channel cursor, invalid nonce/body and methods rejected',async()=>{
 user={id:FOUNDER_GITHUB_ID,login:'xykal'};const f=fixture();
 assert.equal((await f.channel.fetch(req('GET','/channels/not-real/posts'))).status,404);
 assert.equal((await f.channel.fetch(req('GET','/channels/informasi/posts?cursor=channel:announcement:post:abc'))).status,400);
 assert.equal((await f.channel.fetch(req('POST','/channels/informasi/posts',{text:'x',client_post_id:'../x'},'owner'))).status,400);
 assert.equal((await f.channel.fetch(req('POST','/channels/informasi/posts',post('x'.repeat(4001)),'owner'))).status,400);
 assert.equal((await f.channel.fetch(req('DELETE','/channels'))).status,405);
});
test('channel history cache coalesces, invalidates after publish and preserves every older post',async()=>{
 user={id:FOUNDER_GITHUB_ID,login:'xykal'};const f=fixture();
 for(let i=0;i<65;i++){const id=`00000000-0000-4000-8000-${String(i).padStart(12,'0')}`;f.data.set(`channel:informasi:post:${1700000000000+i}:${id}`,{id,text:'p'+i,at:1700000000000+i});}
 const first=await(await f.channel.fetch(req('GET','/channels/informasi/posts'))).json();assert.equal(first.posts.length,30);
 const second=await(await f.channel.fetch(req('GET','/channels/informasi/posts?cursor='+encodeURIComponent(first.next_cursor)))).json();assert.equal(second.posts.length,30);
 const third=await(await f.channel.fetch(req('GET','/channels/informasi/posts?cursor='+encodeURIComponent(second.next_cursor)))).json();assert.equal(third.posts.length,5);assert.equal(third.next_cursor,null);
 assert.equal(new Set([...first.posts,...second.posts,...third.posts].map(x=>x.id)).size,65);
 await f.channel.fetch(req('POST','/channels/informasi/posts',post(),'owner'));
 const fresh=await(await f.channel.fetch(req('GET','/channels/informasi/posts'))).json();assert.equal(fresh.posts[0].id,nonce);assert.equal([...f.data.keys()].filter(x=>x.includes(':post:')).length,66);
});
test('storage quota is explicit 503, not fake empty successful channel',async()=>{
 const c=new OfficialChannels({list:async()=>{throw Error('Exceeded allowed rows read: private detail');}});
 const r=await c.fetch(req('GET','/channels/informasi/posts'));assert.equal(r.status,503);assert.equal(r.headers.get('retry-after'),'300');assert.ok(!(await r.text()).includes('private detail'));
});
test('edge forwards bearer and real route, does not silently fall back',async()=>{
 let forwarded;const env={GLOBAL_CHAT:{idFromName:()=>1,get:()=>({fetch:async r=>{forwarded=r;return new Response('{}');}})}};
 await worker.fetch(req('POST','/api/chat/channels/informasi/posts',post(),'provider-token'),env);
 assert.equal(new URL(forwarded.url).pathname,'/channels/informasi/posts');assert.equal(forwarded.headers.get('authorization'),'Bearer provider-token');assert.equal((await forwarded.json()).text,post().text);
});
test('account switch cannot publish the previous account draft under a new token',async()=>{
 user={id:FOUNDER_GITHUB_ID,login:'xykal'};const f=fixture();
 const r=await f.channel.fetch(req('POST','/channels/announcement/posts',{...post(),expected_login:'different-account'},'owner'));
 assert.equal(r.status,409);assert.equal(f.data.size,0);
});
test('invalid provider token never becomes a staff grant',async()=>{
 globalThis.fetch=async()=>new Response('{}',{status:401});const f=fixture();
 assert.equal((await f.channel.fetch(req('POST','/channels/announcement/posts',post(),'expired'))).status,401);assert.equal(f.data.size,0);
});
test('streamed oversized bodies are rejected before any storage write',async()=>{
 user={id:FOUNDER_GITHUB_ID,login:'xykal'};const f=fixture();
 assert.equal((await f.channel.fetch(req('POST','/channels/announcement/posts',post('x'.repeat(21000)),'owner'))).status,413);assert.equal(f.data.size,0);
});
test('real edge-to-GlobalChat-to-channel integration returns catalogue and both contract headers',async()=>{
 const f=fixture();const chat=new GlobalChat({storage:f.storage},{});
 const env={GLOBAL_CHAT:{idFromName:()=>1,get:()=>({fetch:r=>chat.fetch(r)})}};
 const r=await worker.fetch(req('GET','/api/chat/channels'),env);
 assert.equal(r.status,200);assert.equal(r.headers.get('x-xydesk-channel-router'),'1');assert.equal(r.headers.get('x-xydesk-channel-handler'),'1');
 assert.equal((await r.json()).channels.length,3);
 const denied=await worker.fetch(req('POST','/api/chat/channels/announcement/posts',post()),env);assert.equal(denied.status,401);
});
