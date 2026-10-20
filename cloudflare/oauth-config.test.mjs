import test, {beforeEach,afterEach} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import worker from './worker.mjs';
import {githubOAuthConfig,GITHUB_CALLBACK} from './oauth-config.mjs';
const env={GITHUB_OAUTH_CLIENT_ID:'test-xydesk-client',GITHUB_OAUTH_CLIENT_SECRET:'test-config-not-real'};
const originalFetch=globalThis.fetch;
beforeEach(()=>{globalThis.fetch=async()=>{throw Error('unplanned provider request');};});
afterEach(()=>{globalThis.fetch=originalFetch;});
test('all login aliases/selectors choose the configured client and canonical API callback',async()=>{
 for(const path of ['/api/github/oauth/login','/api/auth/login']) for(const selector of ['', '&client=xydesk','&client=legacy','&redirect_uri=https://example.test']) {
  const r=await worker.fetch(new Request('https://api.xydeskremote.biz.id'+path+'?session_id=test-session'+selector),env);
  assert.equal(r.status,302);const dest=new URL(r.headers.get('location'));
  assert.equal(dest.origin,'https://github.com');assert.equal(dest.pathname,'/login/oauth/authorize');
  assert.equal(dest.searchParams.get('client_id'),env.GITHUB_OAUTH_CLIENT_ID);
  assert.equal(dest.searchParams.get('redirect_uri'),GITHUB_CALLBACK);
  assert.equal(r.headers.get('location').includes(env.GITHUB_OAUTH_CLIENT_SECRET),false);
  assert.equal(r.headers.get('cache-control'),'no-store');
 }
});
test('missing configuration fails closed, never falls back to embedded client or secret',async()=>{
 for(const incomplete of [{},{GITHUB_OAUTH_CLIENT_ID:'test-client'},{GITHUB_OAUTH_CLIENT_SECRET:'test-secret'}]) {
  assert.equal(githubOAuthConfig(incomplete),null);
  for(const path of ['/api/github/oauth/login','/api/github/oauth/callback?code=test-code']) {
   const r=await worker.fetch(new Request('https://api.xydeskremote.biz.id'+path),incomplete);
   assert.equal(r.status,503);assert.equal((await r.json()).code,'oauth_unconfigured');
   assert.equal(r.headers.get('location'),null);
  }
 }
});
test('callback aliases exchange with EXACT same API redirect and only configured credentials',async()=>{
 for(const path of ['/api/github/oauth/callback','/api/auth/callback/github','/api/github/oauth/exchange']) {
  let exchange;
  globalThis.fetch=async(url,options)=>{
   if(String(url)==='https://github.com/login/oauth/access_token') {
    exchange=JSON.parse(options.body);return Response.json({error:'bad_verification_code'});
   }
   throw Error('no identity/session request after rejection');
  };
  const r=await worker.fetch(new Request('https://api.xydeskremote.biz.id'+path+'?code=test-only'),env);
  assert.equal(r.status,400);
  assert.deepEqual(exchange,{...githubOAuthConfig(env),code:'test-only'});
 }
});
test('provider rejection never retries a legacy OAuth app',async()=>{
 let calls=0;globalThis.fetch=async()=>{calls++;return Response.json({error:'bad_verification_code'});};
 const r=await worker.fetch(new Request('https://api.xydeskremote.biz.id/api/github/oauth/callback?code=test-only'),env);
 assert.equal(r.status,400);assert.equal(calls,1);
});
test('Android login source has no legacy dashboard polling or cookie login fallback',async()=>{
 const source=await readFile(new URL('../client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/FounderBadge.kt',import.meta.url),'utf8');
 assert.ok(source.includes('GitHubOAuthEndpoints.login(sessionId)'));
 assert.ok(!source.includes('xyrdp-dash.vercel.app'));assert.ok(!source.includes('rdpfree.projectkal'));
});
test('Worker source contains no fixed legacy callback or embedded OAuth client secret',async()=>{
 const source=await readFile(new URL('./worker.mjs',import.meta.url),'utf8');
 assert.ok(!source.includes('rdpfree.projectkal.my.id/api/auth/callback/github'));
 assert.ok(!/client_secret:\s*["'][a-f0-9]{32,}["']/.test(source));
 assert.ok(!/GITHUB_OAUTH_CLIENT_SECRET\s*\|\|\s*["'][a-f0-9]{32,}/.test(source));
});
