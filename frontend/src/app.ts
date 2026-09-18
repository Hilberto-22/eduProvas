import { ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Client } from '@stomp/stompjs';
import { Api } from './api';
import { AnswerInput, Assessment, Attempt, AttemptStatus, Items, ListKey, Lists, Page, Question, emptyPage } from './models';
import { MonitorRefresh } from './monitor-refresh';

@Component({
  selector:'app-root', standalone:true, imports:[CommonModule,FormsModule],
  templateUrl:'./app.html'
})
export class AppComponent implements OnInit,OnDestroy {
  email=''; password=''; error=''; notice=''; busy=false; page='overview';
  lists: Lists = {classes:emptyPage(), assessments:emptyPage(), sessions:emptyPage(), users:emptyPage(), students:emptyPage(), roster:emptyPage(), history:emptyPage()};
  get classes() { return this.lists.classes.items; }
  get assessments() { return this.lists.assessments.items; }
  get sessions() { return this.lists.sessions.items; }
  get users() { return this.lists.users.items; }
  get students() { return this.lists.students.items; }
  get roster() { return this.lists.roster.items; }
  get history() { return this.lists.history.items; }
  recentSessions:Items['sessions'][]=[];
  private monitorPage=0;
  private listRequests: Partial<Record<ListKey, number>> = {};
  private selections = {classes: new Map<string, Items['classes']>(), assessments: new Map<string, Assessment>(), sessions: new Map<string, Items['sessions']>()};
  private monitorRefresh = new MonitorRefresh(async () => {
    if(this.monitorId && this.page==='monitor' && this.api.user && this.api.user.role!=='ALUNO') {
      await this.loadList('roster', this.monitorPage);
      this.changeDetector.detectChanges();
    }
  });
  className=''; title=''; classId=''; assessmentId=''; questions:Question[]=[]; selectedAssessment:Assessment|null=null;
  newUser={name:'',email:'',password:'',role:'ALUNO'}; enrollmentId='';
  question={prompt:'',kind:'OBJETIVA',points:1}; options=['','']; correct=0;
  sessionForm={assessmentId:'',classId:'',startsAt:'',endsAt:'',durationMinutes:60,maxViolations:3,violationAction:'REGISTRAR'};
  monitorId=''; review:Attempt|null=null; grades:Record<string,{score:number|null;feedback:string}>={}; live=false;
  code=''; attempt:Attempt|null=null; answers:Record<string,AnswerInput>={};
  pending:Record<string,AnswerInput>={}; events:{id:string;kind:string}[]=[]; flushing=false; saveState='Respostas salvas';
  remaining=0; clockOffset=0; fullscreen=!!document.fullscreenElement; confirmSubmit=false;
  private client?:Client; private interval?:ReturnType<typeof setInterval>; private ticks=0; private ticking=false;
  constructor(public api:Api,private changeDetector:ChangeDetectorRef) {
    api.onUnauthorized=()=>{
      this.attempt=null;this.monitorId='';this.api.logout();void this.client?.deactivate();
      this.error='Sua sessão expirou. Entre novamente para retomar a prova. O prazo continua correndo.';
    };
  }
  async ngOnInit() {
    if(this.api.user) await this.run(()=>this.load());
    this.interval=setInterval(()=>{void this.tick().finally(()=>this.changeDetector.detectChanges());},1000);
  }
  ngOnDestroy() { this.monitorRefresh.stop(); clearInterval(this.interval); void this.client?.deactivate(); }
  async run(action:()=>Promise<unknown>) {
    if(this.busy) return;
    this.error=''; this.notice=''; this.busy=true;
    try { await action(); } catch(e) { this.error=e instanceof Error?e.message:'Falha de conexão'; } finally { this.busy=false;this.changeDetector.detectChanges(); }
  }
  async login() { await this.run(async()=>{await this.api.login(this.email,this.password);this.password='';await this.load();}); }
  logout() {
    if(this.active) return;
    void this.client?.deactivate(); this.api.logout(); this.monitorId=''; this.attempt=null; this.review=null; this.page='overview';
  }
  async load() {
    this.lists={classes:emptyPage(),assessments:emptyPage(),sessions:emptyPage(),users:emptyPage(),students:emptyPage(),roster:emptyPage(),history:emptyPage()};
    this.recentSessions=[];
    this.selections.classes.clear();this.selections.assessments.clear();this.selections.sessions.clear();
    this.classId='';this.assessmentId='';this.monitorId='';this.selectedAssessment=null;this.review=null;
    this.sessionForm.classId='';this.sessionForm.assessmentId='';
    if(this.api.user?.role==='ALUNO') {
      await this.loadList('history');
      const active=await this.api.call<{id:string|null}>('/student/active-attempt');
      if(active.id) await this.openAttempt(active.id);
    } else {
      await this.reloadTeacher(); this.connect();
    }
  }
  async reloadTeacher(sessionPage=this.lists.sessions.page) {
    await Promise.all([this.loadList('classes',this.lists.classes.page), this.loadList('assessments',this.lists.assessments.page), this.loadList('sessions',sessionPage)]);
    if(this.api.user?.role==='ADMIN') await this.loadList('users',this.lists.users.page);
  }
  private listPath(key:ListKey):string {
    switch(key) {
      case 'users': return '/admin/users';
      case 'history': return '/student/attempts';
      case 'students': return '/teacher/classes/'+this.classId+'/students';
      case 'roster': return '/teacher/sessions/'+this.monitorId+'/monitor';
      default: return '/teacher/'+key;
    }
  }
  async loadList<K extends ListKey>(key:K, page=0):Promise<void> {
    const path=this.listPath(key), user=this.api.user?.id;
    const request=(this.listRequests[key]||0)+1; this.listRequests[key]=request;
    const result=await this.api.call<Page<Items[K]>>(path+'?page='+page+'&size=10');
    if(this.api.user?.id!==user || this.listRequests[key]!==request || this.listPath(key)!==path) return;
    if(key==='roster' && page!==this.monitorPage) return;
    this.lists[key]=result as Lists[K];
    if(key==='classes') this.rememberOptions(this.classes,this.selections.classes,[this.classId,this.sessionForm.classId]);
    if(key==='assessments') {
      this.rememberOptions(this.assessments,this.selections.assessments,[this.assessmentId,this.sessionForm.assessmentId]);
      this.selectedAssessment=this.selections.assessments.get(this.assessmentId)||null;
    }
    if(key==='sessions') {
      this.rememberOptions(this.sessions,this.selections.sessions,[this.monitorId]);
      if(page===0) this.recentSessions=this.sessions.slice(0,5);
    }
  }
  async changePage(key:ListKey, delta:number) {
    await this.run(async()=>{
      const next=this.lists[key].page+delta;
      if(next<0 || next*10>=this.lists[key].total) return;
      if(key==='roster') {
        this.monitorPage=next;
        await this.refreshMonitor();
      } else await this.loadList(key,next);
    });
  }
  listInfo(key:ListKey) { return this.lists[key]; }
  pageCount(key:ListKey) { return Math.max(1,Math.ceil(this.lists[key].total/this.lists[key].size)); }
  classOptions() { return this.withSelected(this.classes,this.selections.classes,[this.classId,this.sessionForm.classId]); }
  assessmentOptions() { return this.withSelected(this.assessments,this.selections.assessments,[this.assessmentId,this.sessionForm.assessmentId]); }
  sessionOptions() { return this.withSelected(this.sessions,this.selections.sessions,[this.monitorId]); }
  private rememberOptions<T extends {id:string}>(items:T[],cache:Map<string,T>,ids:string[]) {
    const selected=ids.map(id=>cache.get(id)).filter((item):item is T=>!!item);
    cache.clear();
    for(const item of [...selected,...items]) cache.set(item.id,item);
  }
  private withSelected<T extends {id:string}>(items:T[], cache:Map<string,T>, ids:string[]):T[] {
    const selected=ids.filter((id,index)=>id && ids.indexOf(id)===index && !items.some(item=>item.id===id)).map(id=>cache.get(id)).filter((item):item is T=>!!item);
    return [...selected,...items];
  }
  connect() {
    if(this.client?.active) return;
    this.client=new Client({
      brokerURL:(location.protocol==='https:'?'wss://':'ws://')+location.host+'/ws',
      connectHeaders:{Authorization:'Bearer '+this.api.token}, reconnectDelay:5000,
      onConnect:()=>{this.live=true; this.client!.subscribe('/user/queue/monitor',message=>{
        try { if(JSON.parse(message.body).sessionId===this.monitorId) this.monitorRefresh.schedule(); } catch {}
      }); if(this.monitorId) void this.refreshMonitor().catch(()=>{});},
      onWebSocketClose:()=>this.live=false, onStompError:()=>this.live=false
    }); this.client.activate();
  }
  async createClass() { await this.run(async()=>{await this.api.call('/teacher/classes','POST',{name:this.className});this.className='';await this.reloadTeacher();}); }
  async selectClass() { this.lists.students=emptyPage(); if(this.classId) await this.loadList('students'); }
  async createUser(admin=false) { await this.run(async()=>{
    await this.api.call(admin?'/admin/users':'/teacher/classes/'+this.classId+'/students','POST',{...this.newUser,role:admin?this.newUser.role:'ALUNO'});
    this.newUser={name:'',email:'',password:'',role:'ALUNO'};
    if(!admin) await this.selectClass();
    await this.reloadTeacher();this.notice='Cadastro concluído.';
  }); }
  async enroll() { await this.run(async()=>{await this.api.call('/teacher/classes/'+this.classId+'/enrollments','POST',{studentId:this.enrollmentId});await this.selectClass();this.enrollmentId='';}); }
  async createAssessment() { await this.run(async()=>{
    const title=this.title.trim();const a=await this.api.call<{id:string}>('/teacher/assessments','POST',{title});this.assessmentId=a.id;this.selections.assessments.set(a.id,{id:a.id,title,teacher_id:this.api.user!.id,locked:false});this.title='';await this.reloadTeacher();await this.selectAssessment();
  }); }
  async selectAssessment() {
    this.selectedAssessment=this.selections.assessments.get(this.assessmentId)||null;
    this.questions=this.assessmentId?await this.api.call<Question[]>('/teacher/assessments/'+this.assessmentId+'/questions'):[];
  }
  async addQuestion() { await this.run(async()=>{
    await this.api.call('/teacher/assessments/'+this.assessmentId+'/questions','POST',{
      ...this.question, alternatives:this.question.kind==='OBJETIVA'?this.options.map((label,i)=>({label,correct:i===Number(this.correct)})):[]
    });
    this.question={prompt:'',kind:'OBJETIVA',points:1};this.options=['',''];this.correct=0;await this.selectAssessment();
  }); }
  addOption() {
    if(this.options.length<5) this.options.push('');
  }
  removeOption(index:number) {
    if(this.options.length<=2) return;
    this.options.splice(index,1);
    if(this.correct===index) this.correct=0;
    else if(this.correct>index) this.correct--;
  }
  async createSession() { await this.run(async()=>{
    await this.api.call('/teacher/sessions','POST',{...this.sessionForm,startsAt:new Date(this.sessionForm.startsAt).toISOString(),endsAt:new Date(this.sessionForm.endsAt).toISOString()});
    const assessment=this.selections.assessments.get(this.sessionForm.assessmentId);
    if(assessment) assessment.locked=true;
    if(this.selectedAssessment?.id===this.sessionForm.assessmentId) this.selectedAssessment.locked=true;
    await this.reloadTeacher(0);this.notice='Aplicação criada. Publique para liberar o código aos alunos.';
  }); }
  async publish(id:string) { await this.run(async()=>{await this.api.call('/teacher/sessions/'+id+'/publish','POST',{});await this.reloadTeacher();}); }
  async monitor(id:string) { this.monitorId=id;this.page='monitor';await this.run(()=>this.selectMonitor()); }
  async selectMonitor() { this.monitorPage=0;this.lists.roster=emptyPage();this.review=null;await this.refreshMonitor(); }
  async refreshMonitor() { await this.monitorRefresh.flush(); }
  async openReview(id:string) { await this.run(async()=>{
    this.review=await this.api.call<Attempt>('/teacher/attempts/'+id);this.grades={};
    for(const a of this.review.answers) this.grades[a.question_id]={score:a.score,feedback:a.feedback||''};
  }); }
  reviewAnswer(id:string) { return this.review?.answers.find(a=>a.question_id===id); }
  async grade(id:string) { await this.run(async()=>{
    await this.api.call<void>('/teacher/attempts/'+this.review!.id+'/grades/'+id,'PUT',this.grades[id]);
    this.review=await this.api.call<Attempt>('/teacher/attempts/'+this.review!.id);this.notice='Correção salva.';
  }); }
  get active() { return this.attempt?.status==='EM_ANDAMENTO'; }
  get draftKey() { return 'draft:'+this.api.user?.id+':'+this.attempt?.id; }
  async enterFullscreen() {
    if(!document.fullscreenElement) await document.documentElement.requestFullscreen();
    this.fullscreen=!!document.fullscreenElement;
  }
  async join() { await this.run(async()=>{
    await this.enterFullscreen();
    try { const a=await this.api.call<Attempt>('/student/join','POST',{code:this.code});this.setAttempt(a,true); }
    catch(e) { if(document.fullscreenElement) await document.exitFullscreen();throw e; }
  }); }
  async openAttempt(id:string) { const a=await this.api.call<Attempt>('/student/attempts/'+id);this.setAttempt(a,true); }
  setAttempt(a:Attempt,restore=false) {
    this.attempt=a;this.clockOffset=Date.parse(a.server_now)-Date.now();
    if(restore) {
      this.answers={};this.pending={};this.events=[];
      for(const r of a.answers) this.answers[r.question_id]={alternativeId:r.alternative_id,text:r.text_value};
      if(this.active) {
        try { const draft=JSON.parse(sessionStorage.getItem(this.draftKey)||'{}');this.pending=draft.pending||{};this.events=draft.events||[]; } catch {}
        Object.assign(this.answers,this.pending);
      }
    }
    if(!this.active) {
      this.confirmSubmit=false;
      if(Object.keys(this.pending).length) this.error='A prova foi encerrada. Alterações que não chegaram ao servidor não foram incluídas no resultado.';
      this.pending={};this.events=[];sessionStorage.removeItem(this.draftKey);
      if(document.fullscreenElement) void document.exitFullscreen().catch(()=>{});
    }
    this.updateClock();
  }
  updateClock() { if(this.attempt) this.remaining=Math.max(0,Math.ceil((Date.parse(this.attempt.deadline)-Date.now()-this.clockOffset)/1000)); }
  get timer() { return Math.floor(this.remaining/60).toString().padStart(2,'0')+':'+(this.remaining%60).toString().padStart(2,'0'); }
  get answered() { return Object.values(this.answers).filter(a=>a.alternativeId||a.text?.trim()).length; }
  change(q:Question,value:string) {
    this.answers[q.id]=q.kind==='OBJETIVA'?{alternativeId:value,text:null}:{alternativeId:null,text:value};
    this.pending[q.id]={...this.answers[q.id]};this.saveState='Alterações aguardando envio';this.persist();
  }
  persist() { sessionStorage.setItem(this.draftKey,JSON.stringify({pending:this.pending,events:this.events})); }
  async flush() {
    if(this.flushing||!this.active) return;
    this.flushing=true;
    try {
      while(this.events.length && this.active) {
        const event=this.events[0];
        const r=await this.api.call<{status:string;violations:number}>('/student/attempts/'+this.attempt!.id+'/occurrences','POST',event);
        this.events.shift();this.persist();this.attempt!.violations=r.violations;
        if(r.status==='FINALIZADA') { this.setAttempt(await this.api.call<Attempt>('/student/attempts/'+this.attempt!.id));break; }
      }
      for(const [id,value] of Object.entries(this.pending)) {
        if(!this.active) break;
        const r=await this.api.call<{accepted:boolean}>('/student/attempts/'+this.attempt!.id+'/answers/'+id,'PUT',value);
        if(!r.accepted) { this.setAttempt(await this.api.call<Attempt>('/student/attempts/'+this.attempt!.id));break; }
        if(JSON.stringify(this.pending[id])===JSON.stringify(value)) delete this.pending[id];
        this.persist();
      }
      this.saveState=Object.keys(this.pending).length?'Alterações aguardando envio':'Respostas salvas';
    } catch { this.saveState='Sem confirmação do servidor. Tentando novamente…'; }
    finally { this.flushing=false; }
  }
  record(kind:string) {
    if(!this.active) return;
    this.events.push({id:crypto.randomUUID(),kind});this.persist();
    this.notice='Saída do modo prova detectada e registrada.';void this.flush();
  }
  @HostListener('window:blur') blur() { this.record('FOCO_PERDIDO'); }
  @HostListener('document:visibilitychange') visibility() { if(document.hidden) this.record('ABA_OCULTA'); }
  @HostListener('document:fullscreenchange') fullChange() { this.fullscreen=!!document.fullscreenElement;if(!this.fullscreen) this.record('SAIDA_FULLSCREEN'); }
  @HostListener('window:beforeunload',['$event']) beforeUnload(event:BeforeUnloadEvent) { if(this.active) { this.persist();event.preventDefault();event.returnValue=''; } }
  async tick() {
    this.updateClock();
    if(this.ticking) return;
    this.ticking=true;
    try {
    this.ticks++;
    if(this.active) {
      await this.flush();
      if(!this.active) return;
      if(this.ticks%10===0 || this.remaining===0) {
        try { await this.refreshAttemptStatus(); } catch { this.saveState='Conexão indisponível. O prazo continua correndo no servidor.'; }
      }
    }
    if(this.monitorId && this.page==='monitor' && this.api.user && this.api.user.role!=='ALUNO' && this.ticks%15===0) this.monitorRefresh.schedule();
    } finally { this.ticking=false; }
  }
  private async refreshAttemptStatus() {
    const id=this.attempt?.id;
    if(!id) return;
    const status=await this.api.call<AttemptStatus>('/student/attempts/'+id+'/status');
    if(this.attempt?.id!==id || !this.active) return;
    if(status.status==='FINALIZADA') this.setAttempt(await this.api.call<Attempt>('/student/attempts/'+id));
    else {
      // Keep local edits and immutable exam content intact.
      this.attempt={...this.attempt,...status};
      this.clockOffset=Date.parse(status.server_now)-Date.now();
      this.updateClock();
    }
  }
  async submit() { await this.run(async()=>{
    if(this.flushing) throw new Error('Aguarde o salvamento em andamento e tente novamente.');
    await this.flush();
    if(Object.keys(this.pending).length||this.events.length) throw new Error('Há envios pendentes. Reconecte e aguarde o salvamento antes de finalizar.');
    if(this.active) this.setAttempt(await this.api.call<Attempt>('/student/attempts/'+this.attempt!.id+'/submit','POST',{}));
    await this.loadList('history');
  }); }
  async backToHistory() { this.attempt=null;await this.loadList('history'); }
  trackIndex(index:number) { return index; }
}
