import { ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Client } from '@stomp/stompjs';
import { Api } from './api';

@Component({
  selector:'app-root', standalone:true, imports:[CommonModule,FormsModule],
  templateUrl:'./app.html'
})
export class AppComponent implements OnInit,OnDestroy {
  email=''; password=''; error=''; notice=''; busy=false; page='overview';
  classes:any[]=[]; assessments:any[]=[]; sessions:any[]=[]; users:any[]=[]; students:any[]=[];
  className=''; title=''; classId=''; assessmentId=''; questions:any[]=[]; selectedAssessment:any=null;
  newUser={name:'',email:'',password:'',role:'ALUNO'}; enrollmentId='';
  question={prompt:'',kind:'OBJETIVA',points:1}; options=['','']; correct=0;
  sessionForm={assessmentId:'',classId:'',startsAt:'',endsAt:'',durationMinutes:60,maxViolations:3,violationAction:'REGISTRAR'};
  monitorId=''; roster:any[]=[]; review:any=null; grades:Record<string,any>={}; live=false;
  code=''; history:any[]=[]; attempt:any=null; answers:Record<string,any>={};
  pending:Record<string,any>={}; events:any[]=[]; flushing=false; saveState='Respostas salvas';
  remaining=0; clockOffset=0; fullscreen=!!document.fullscreenElement; confirmSubmit=false;
  private client?:Client; private interval:any; private ticks=0; private ticking=false;
  constructor(public api:Api,private changeDetector:ChangeDetectorRef) {
    api.onUnauthorized=()=>{
      this.attempt=null;this.api.logout();void this.client?.deactivate();
      this.error='Sua sessão expirou. Entre novamente para retomar a prova. O prazo continua correndo.';
    };
  }
  async ngOnInit() {
    if(this.api.user) await this.run(()=>this.load());
    this.interval=setInterval(()=>{void this.tick().finally(()=>this.changeDetector.detectChanges());},1000);
  }
  ngOnDestroy() { clearInterval(this.interval); void this.client?.deactivate(); }
  async run(action:()=>Promise<any>) {
    if(this.busy) return;
    this.error=''; this.notice=''; this.busy=true;
    try { await action(); } catch(e:any) { this.error=e.message || 'Falha de conexão'; } finally { this.busy=false;this.changeDetector.detectChanges(); }
  }
  async login() { await this.run(async()=>{await this.api.login(this.email,this.password);this.password='';await this.load();}); }
  logout() {
    if(this.active) return;
    void this.client?.deactivate(); this.api.logout(); this.attempt=null; this.review=null; this.page='overview';
  }
  async load() {
    if(this.api.user?.role==='ALUNO') {
      this.history=await this.api.call('/student/attempts');
      const active=this.history.find(x=>x.status==='EM_ANDAMENTO');
      if(active) await this.openAttempt(active.id);
    } else {
      await this.reloadTeacher(); this.connect();
    }
  }
  async reloadTeacher() {
    [this.classes,this.assessments,this.sessions]=await Promise.all([
      this.api.call('/teacher/classes'),this.api.call('/teacher/assessments'),this.api.call('/teacher/sessions')
    ]);
    if(this.api.user?.role==='ADMIN') this.users=await this.api.call('/admin/users');
  }
  connect() {
    if(this.client?.active) return;
    this.client=new Client({
      brokerURL:(location.protocol==='https:'?'wss://':'ws://')+location.host+'/ws',
      connectHeaders:{Authorization:'Bearer '+this.api.token}, reconnectDelay:5000,
      onConnect:()=>{this.live=true; this.client!.subscribe('/user/queue/monitor',()=>{
        if(this.monitorId) void this.refreshMonitor().catch(()=>{});
      }); if(this.monitorId) void this.refreshMonitor().catch(()=>{});},
      onWebSocketClose:()=>this.live=false, onStompError:()=>this.live=false
    }); this.client.activate();
  }
  async createClass() { await this.run(async()=>{await this.api.call('/teacher/classes','POST',{name:this.className});this.className='';await this.reloadTeacher();}); }
  async selectClass() { this.students=this.classId?await this.api.call('/teacher/classes/'+this.classId+'/students'):[]; }
  async createUser(admin=false) { await this.run(async()=>{
    await this.api.call(admin?'/admin/users':'/teacher/classes/'+this.classId+'/students','POST',{...this.newUser,role:admin?this.newUser.role:'ALUNO'});
    this.newUser={name:'',email:'',password:'',role:'ALUNO'};
    if(!admin) await this.selectClass();
    await this.reloadTeacher();this.notice='Cadastro concluído.';
  }); }
  async enroll() { await this.run(async()=>{await this.api.call('/teacher/classes/'+this.classId+'/enrollments','POST',{studentId:this.enrollmentId});await this.selectClass();this.enrollmentId='';}); }
  async createAssessment() { await this.run(async()=>{
    const a=await this.api.call('/teacher/assessments','POST',{title:this.title});this.title='';await this.reloadTeacher();this.assessmentId=a.id;await this.selectAssessment();
  }); }
  async selectAssessment() {
    this.selectedAssessment=this.assessments.find(a=>a.id===this.assessmentId);
    this.questions=this.assessmentId?await this.api.call('/teacher/assessments/'+this.assessmentId+'/questions'):[];
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
    await this.reloadTeacher();this.notice='Aplicação criada. Publique para liberar o código aos alunos.';
  }); }
  async publish(id:string) { await this.run(async()=>{await this.api.call('/teacher/sessions/'+id+'/publish','POST',{});await this.reloadTeacher();}); }
  async monitor(id:string) { this.monitorId=id;this.page='monitor';await this.run(()=>this.refreshMonitor()); }
  async refreshMonitor() { if(this.monitorId) this.roster=await this.api.call('/teacher/sessions/'+this.monitorId+'/monitor'); }
  async openReview(id:string) { await this.run(async()=>{
    this.review=await this.api.call('/teacher/attempts/'+id);this.grades={};
    for(const a of this.review.answers) this.grades[a.question_id]={score:a.score,feedback:a.feedback||''};
  }); }
  reviewAnswer(id:string) { return this.review?.answers.find((a:any)=>a.question_id===id); }
  async grade(id:string) { await this.run(async()=>{
    await this.api.call('/teacher/attempts/'+this.review.id+'/grades/'+id,'PUT',this.grades[id]);
    this.review=await this.api.call('/teacher/attempts/'+this.review.id);this.notice='Correção salva.';
  }); }
  get active() { return this.attempt?.status==='EM_ANDAMENTO'; }
  get draftKey() { return 'draft:'+this.api.user?.id+':'+this.attempt?.id; }
  async enterFullscreen() {
    if(!document.fullscreenElement) await document.documentElement.requestFullscreen();
    this.fullscreen=!!document.fullscreenElement;
  }
  async join() { await this.run(async()=>{
    await this.enterFullscreen();
    try { const a=await this.api.call('/student/join','POST',{code:this.code});this.setAttempt(a,true); }
    catch(e) { if(document.fullscreenElement) await document.exitFullscreen();throw e; }
  }); }
  async openAttempt(id:string) { const a=await this.api.call('/student/attempts/'+id);this.setAttempt(a,true); }
  setAttempt(a:any,restore=false) {
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
  get answered() { return Object.values(this.answers).filter((a:any)=>a.alternativeId||a.text?.trim()).length; }
  change(q:any,value:string) {
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
        const r=await this.api.call('/student/attempts/'+this.attempt.id+'/occurrences','POST',event);
        this.events.shift();this.persist();this.attempt.violations=r.violations;
        if(r.status==='FINALIZADA') { this.setAttempt(await this.api.call('/student/attempts/'+this.attempt.id));break; }
      }
      for(const [id,value] of Object.entries(this.pending)) {
        if(!this.active) break;
        const r=await this.api.call('/student/attempts/'+this.attempt.id+'/answers/'+id,'PUT',value);
        if(!r.accepted) { this.setAttempt(await this.api.call('/student/attempts/'+this.attempt.id));break; }
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
        try { this.setAttempt(await this.api.call('/student/attempts/'+this.attempt.id)); } catch { this.saveState='Conexão indisponível. O prazo continua correndo no servidor.'; }
      }
    }
    if(this.monitorId && this.api.user?.role!=='ALUNO' && this.ticks%15===0) void this.refreshMonitor().catch(()=>{});
    } finally { this.ticking=false; }
  }
  async submit() { await this.run(async()=>{
    if(this.flushing) throw new Error('Aguarde o salvamento em andamento e tente novamente.');
    await this.flush();
    if(Object.keys(this.pending).length||this.events.length) throw new Error('Há envios pendentes. Reconecte e aguarde o salvamento antes de finalizar.');
    if(this.active) this.setAttempt(await this.api.call('/student/attempts/'+this.attempt.id+'/submit','POST',{}));
    this.history=await this.api.call('/student/attempts');
  }); }
  async backToHistory() { this.attempt=null;this.history=await this.api.call('/student/attempts'); }
  trackIndex(index:number) { return index; }
}
