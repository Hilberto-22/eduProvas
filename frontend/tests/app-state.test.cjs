const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');
const vm = require('node:vm');

function component(call,role='ALUNO') {
  const storage=new Map();
  const context=vm.createContext({setTimeout,clearTimeout,setInterval,clearInterval,
    document:{fullscreenElement:null},location:{protocol:'http:',host:'localhost'},
    sessionStorage:{getItem:key=>storage.get(key),setItem:(key,value)=>storage.set(key,value),removeItem:key=>storage.delete(key)}});
  const modules={};
  const load=name=>{
    if(modules[name]) return modules[name];
    if(name==='@angular/core') return {Component:()=>target=>target,HostListener:()=>()=>{}};
    if(name==='@angular/common'||name==='@angular/forms'||name==='./api') return {};
    if(name==='@stomp/stompjs') return {Client:class {
      constructor(options){this.options=options;} activate(){this.active=true;} subscribe(path,callback){this.callback=callback;}
    }};
    const exports={};
    const source=ts.transpileModule(fs.readFileSync('src/'+name.replace('./','')+'.ts','utf8'),{
      compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}
    }).outputText;
    vm.runInContext('(function(require,exports){'+source+'\n})',context)(load,exports);
    modules[name]=exports;return exports;
  };
  const {AppComponent}=load('./app');
  return new AppComponent({call,user:{id:'user',role},token:'test'},{detectChanges(){}});
}
const page=items=>({items,page:0,size:25,total:items.length});
const active={id:'attempt',status:'EM_ANDAMENTO',deadline:new Date(Date.now()+60000).toISOString(),questions:[{id:'question'}],answers:[],session:{},violations:0};

test('status polling preserves local answers and exam content without fetching a full snapshot', async()=>{
  const calls=[];
  const app=component(async path=>{calls.push(path);return {id:active.id,status:active.status,deadline:active.deadline,server_now:new Date().toISOString(),violations:2};});
  app.attempt=active;app.pending={question:{text:'unsaved'}};app.answers=app.pending;
  await app.refreshAttemptStatus();
  assert.deepEqual(calls,['/student/attempts/attempt/status']);
  assert.equal(app.pending.question.text,'unsaved');assert.equal(app.attempt.questions,active.questions);
  assert.equal(app.attempt.violations,2);
});

test('completion fetches the full result once',async()=>{
  const calls=[];
  const app=component(async path=>{
    calls.push(path);
    return {...active,status:'FINALIZADA',server_now:new Date().toISOString(),score:{total:2,pending:0},max_score:2};
  });
  app.attempt=active;
  await app.refreshAttemptStatus();
  assert.deepEqual(calls,['/student/attempts/attempt/status','/student/attempts/attempt']);
  assert.equal(app.attempt.score.total,2);
});

test('selected classes remain available when navigating to another page',async()=>{
  const app=component(async path=>path.includes('page=1')?{...page([{id:'second',name:'Second'}]),page:1,total:26}:{...page([{id:'first',name:'First'}]),total:26},'PROFESSOR');
  await app.loadList('classes');app.classId='first';
  await app.loadList('classes',1);
  assert.equal(app.classOptions().map(item=>item.id).join(','),'first,second');
  assert.equal(app.lists.classes.total,26);assert.equal(app.classes.length,1);
});

test('a late response for a previous class cannot overwrite the selected class',async()=>{
  let release;
  const app=component(path=>path.includes('/old/')?new Promise(resolve=>release=resolve):Promise.resolve(page([{id:'new-student'}])),'PROFESSOR');
  app.classId='old';const old=app.loadList('students');
  app.classId='new';await app.loadList('students');release(page([{id:'old-student'}]));await old;
  assert.equal(app.students[0].id,'new-student');
});

test('overview retains recent sessions when the applications list changes page',async()=>{
  const app=component(async path=>path.includes('page=1')?{...page([{id:'older'}]),page:1,total:26}:{...page([{id:'recent'}]),total:26},'PROFESSOR');
  await app.loadList('sessions');await app.loadList('sessions',1);
  assert.equal(app.sessions[0].id,'older');assert.equal(app.recentSessions[0].id,'recent');
  assert.equal(app.selections.sessions.size,1);
});

test('WebSocket ignores other sessions and groups notifications for the selected session',()=>{
  const app=component(async()=>page([]),'PROFESSOR');let schedules=0;
  app.monitorId='selected';app.monitorRefresh={schedule(){schedules++;},async flush(){}};
  app.connect();app.client.options.onConnect();
  app.client.callback({body:JSON.stringify({sessionId:'another'})});
  assert.equal(schedules,0);
  app.client.callback({body:JSON.stringify({sessionId:'selected'})});
  assert.equal(schedules,1);
});

test('resuming an active attempt does not depend on the first history page',async()=>{
  const app=component(async path=>path==='/student/active-attempt'?{id:'older-active'}:page([{id:'recent',status:'FINALIZADA'}]));
  let opened;app.openAttempt=async id=>{opened=id;};
  await app.load();assert.equal(opened,'older-active');
});
