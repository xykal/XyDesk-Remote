import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const html = fs.readFileSync(new URL('../web/index.html', import.meta.url), 'utf8');
function harness() {
  let next = 0;
  const timers = new Map(), requests = [], events = {};
  const ctx = {
    document: {hidden:false, addEventListener:(name, fn)=>events[name]=fn},
    AbortController, Number, Error,
    setTimeout:(fn, ms)=>{timers.set(++next,{fn,ms});return next;},
    clearTimeout:id=>timers.delete(id),
    fetch:(url, options)=>new Promise((resolve,reject)=>{
      const request={url,options,resolve,reject};requests.push(request);
      options.signal.addEventListener('abort',()=>reject(new Error('aborted')));
    }),
    latestActiveSessionStats:null, activeSessionStatsFailed:false,
    renderActiveSessionStats:()=>{}
  };
  vm.createContext(ctx);
  vm.runInContext(html.slice(html.indexOf('      var activeStatsInFlight'),html.indexOf('      function renderDownloadStats()')),ctx);
  return {ctx,timers,requests,events};
}
const settle = () => new Promise(resolve=>setImmediate(resolve));
test('expired popup and chat presence polling are absent',()=>{
  assert.ok(!html.includes('xyCdWrap'));
  assert.ok(html.includes('main code { overflow-wrap: anywhere;'));
  assert.ok(!html.includes('/api/chat/presence'));
  assert.ok(!html.includes('setInterval('));
  assert.ok(!html.includes('content-visibility: auto')); // Must not mis-target download anchors.
});
test('hidden tabs do not fetch or schedule recurring work',()=>{
 const h=harness();h.ctx.document.hidden=true;h.ctx.loadActiveSessionStats();h.ctx.scheduleActiveStats();
 assert.equal(h.requests.length,0);assert.equal(h.timers.size,0);
});
test('single in-flight request, honest zero, two minute schedule',async()=>{
 const h=harness();h.ctx.loadActiveSessionStats();h.ctx.loadActiveSessionStats();
 assert.equal(h.requests.length,1);
 h.requests[0].resolve({ok:true,json:async()=>({active_session_count:0})});await settle();
 assert.equal(h.ctx.latestActiveSessionStats.active_session_count,0);
 // 120 dtk (bukan 60) untuk menghemat kuota Workers harian; lihat Error 1027.
 assert.equal(h.timers.size,1);assert.equal([...h.timers.values()][0].ms,120000);
});
test('rapid polling is debounced to the 30 second minimum gap',async()=>{
 const h=harness();h.ctx.loadActiveSessionStats();
 h.requests[0].resolve({ok:true,json:async()=>({active_session_count:1})});await settle();
 // Panggil lagi langsung setelah request pertama selesai: harus dijadwalkan
 // ulang, bukan memicu request kedua.
 h.ctx.loadActiveSessionStats();
 assert.equal(h.requests.length,1);
 // Tapi saat tab kembali terlihat, force=true tetap mengambil angka segar.
 h.ctx.loadActiveSessionStats(true);
 assert.equal(h.requests.length,2);
});
test('HTTP errors and invalid counts never fabricate online users',async()=>{
 for (const response of [{ok:false},{ok:true,json:async()=>({active_session_count:-1})},{ok:true,json:async()=>({})}]) {
  const h=harness();h.ctx.loadActiveSessionStats();h.requests[0].resolve(response);await settle();
  assert.equal(h.ctx.latestActiveSessionStats,null);assert.equal(h.ctx.activeSessionStatsFailed,true);
 }
});
test('visibility aborts request, clears work, resume fetches once',async()=>{
 const h=harness();h.ctx.loadActiveSessionStats();h.ctx.document.hidden=true;h.events.visibilitychange();await settle();
 assert.equal(h.requests[0].options.signal.aborted,true);assert.equal(h.timers.size,0);
 h.ctx.document.hidden=false;h.events.visibilitychange();assert.equal(h.requests.length,2);
 h.requests[1].resolve({ok:true,json:async()=>({active_session_count:3})});await settle();
 assert.equal(h.timers.size,1);
});
test('hung request aborts after eight seconds and remains bounded',async()=>{
 const h=harness();h.ctx.loadActiveSessionStats();const timeout=[...h.timers.values()].find(t=>t.ms===8000);timeout.fn();await settle();
 assert.equal(h.requests[0].options.signal.aborted,true);assert.equal(h.ctx.activeStatsInFlight,false);
 assert.equal(h.timers.size,1);
});
