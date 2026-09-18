const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');
const vm = require('node:vm');
const source = ts.transpileModule(fs.readFileSync('src/app/features/teaching/services/monitor-refresh.ts','utf8'), {
  compilerOptions: {module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}
}).outputText;
const context = {exports:{},setTimeout,clearTimeout};
vm.runInNewContext(source,context);
const {MonitorRefresh} = context.exports;
const pause = ms => new Promise(resolve=>setTimeout(resolve,ms));

test('bursts share one refresh and stop cancels scheduled work', async () => {
  let calls=0;
  const refresh=new MonitorRefresh(async()=>{calls++;},5);
  for(let i=0;i<30;i++) refresh.schedule();
  await pause(30);
  assert.equal(calls,1);
  refresh.schedule();refresh.stop();
  await pause(20);
  assert.equal(calls,1);
});

test('events during a request trigger only one trailing request, without overlap', async () => {
  let release, calls=0, concurrent=0, maximum=0;
  const refresh=new MonitorRefresh(async()=>{
    calls++; concurrent++; maximum=Math.max(maximum,concurrent);
    if(calls===1) await new Promise(resolve=>release=resolve);
    concurrent--;
  },5);
  const first=refresh.flush();
  for(let i=0;i<20;i++) refresh.schedule();
  release(); await first; await pause(30);
  assert.equal(calls,2);assert.equal(maximum,1);refresh.stop();
});

test('failed requests do not block future refreshes', async () => {
  let calls=0;
  const refresh=new MonitorRefresh(async()=>{if(++calls===1) throw Error('offline');},5);
  await assert.rejects(refresh.flush());
  await refresh.flush();
  assert.equal(calls,2);refresh.stop();
});
