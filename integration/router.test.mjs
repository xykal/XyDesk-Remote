import {test} from 'node:test';
import assert from 'node:assert/strict';
import router,{isNextPath,isWebHost} from './router.mjs';
import legacy from '../cloudflare/worker.mjs';

test('only intended UI routes go to Next, never API or release aliases',()=>{
 for(const p of ['/','/index.html','/about.html','/_next/static/x.js','/legacy-nav.css','/sw-next.js','/lang.js']) assert.equal(isNextPath(p),true,p);
 for(const p of ['/api/health','/api/releases','/arm64-v8a.apk','/universal.apk','/ws/chat','/host','/host.html','/host.ps1']) assert.equal(isNextPath(p),false,p);
});
test('API hosts never serve Next home',()=>{
 for(const h of ['api.xydeskremote.biz.id','wss.xydeskremote.biz.id','cpc.xydeskremote.biz.id','xydeskremote.biz.id']) assert.equal(isWebHost(h),false,h);
 assert.equal(isWebHost('www.xydeskremote.biz.id'),true);
});
test('Next response has existing security headers and index alias resolves to home', async()=>{
 let path;const env={NEXT_SITE:{fetch:async req=>{path=new URL(req.url).pathname;return new Response('<html>ok</html>',{headers:{'Content-Type':'text/html'}})}}};
 const r=await router.fetch(new Request('https://www.xydeskremote.biz.id/index.html?q=1'),env,{});
 assert.equal(path,'/');assert.equal(r.headers.get('x-frame-options'),'SAMEORIGIN');assert.match(r.headers.get('content-security-policy'),/frame-ancestors 'self'/);
});
test('legacy API health still handled by old Worker, not Next',async()=>{
 let called=false;const r=await router.fetch(new Request('https://api.xydeskremote.biz.id/api/health'),{NEXT_SITE:{fetch(){called=true;throw Error('bad route')}}},{});
 assert.equal(called,false);const original = await legacy.fetch(new Request('https://api.xydeskremote.biz.id/api/health'),{},{});assert.equal(r.status,original.status);assert.deepEqual(await r.json(),await original.json());
});

test('preview may embed but production-like response retains frame protections', async()=>{
 const env={NEXT_SITE:{fetch:async()=>new Response('ok',{headers:{'content-type':'text/html'}})}};
 const p=await router.fetch(new Request('https://3001-test.e2b.app/'),env,{});
 assert.equal(p.headers.get('x-frame-options'),null);assert.doesNotMatch(p.headers.get('content-security-policy'),/frame-ancestors/);
 const prod=await router.fetch(new Request('https://www.xydeskremote.biz.id/'),env,{});
 assert.equal(prod.headers.get('x-frame-options'),'SAMEORIGIN');assert.match(prod.headers.get('content-security-policy'),/frame-ancestors/);
});
