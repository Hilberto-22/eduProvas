package br.edu.avaliacoes;

import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import br.edu.avaliacoes.service.AttemptService;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="RUN_INTEGRATION_TESTS",matches="true")
class FlowIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;
    @Autowired AttemptService attempts;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired org.springframework.messaging.simp.user.SimpUserRegistry registry;
    String admin,teacher,student,otherStudent,otherTeacher;
    UUID classId,assessmentId,questionId,essayId,correctId;
    String password="Test-password-12345";
    ResponseEntity<Map> request(String token,String method,String path,Object body) {
        var headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if(token!=null) headers.setBearerAuth(token);
        return http.exchange("/api"+path,HttpMethod.valueOf(method),new HttpEntity<>(body,headers),Map.class);
    }
    Map ok(String token,String method,String path,Object body) {
        var r=request(token,method,path,body);
        assertThat(r.getStatusCode().is2xxSuccessful()).as(path+" "+r.getBody()).isTrue();
        return r.getBody();
    }
    String login(String email,String pass) { return (String)ok(null,"POST","/auth/login",Map.of("email",email,"password",pass)).get("token"); }
    UUID uuid(Map map) { return UUID.fromString(map.get("id").toString()); }
    String create(String role,String email) {
        ok(admin,"POST","/admin/users",Map.of("name",role,"email",email,"password",password,"role",role));
        return login(email,password);
    }
    @BeforeEach void setup() {
        admin=login(System.getenv().getOrDefault("ADMIN_EMAIL","admin@escola.local"),System.getenv("ADMIN_PASSWORD"));
        String suffix=UUID.randomUUID().toString().substring(0,8);
        teacher=create("PROFESSOR","p"+suffix+"@test.local");
        otherTeacher=create("PROFESSOR","p2"+suffix+"@test.local");
        otherStudent=create("ALUNO","a2"+suffix+"@test.local");
        classId=uuid(ok(teacher,"POST","/teacher/classes",Map.of("name","Turma teste")));
        ok(teacher,"POST","/teacher/classes/"+classId+"/students",Map.of("name","Aluno","email","a"+suffix+"@test.local","password",password,"role","ALUNO"));
        student=login("a"+suffix+"@test.local",password);
        assessmentId=uuid(ok(teacher,"POST","/teacher/assessments",Map.of("title","Teste")));
        questionId=uuid(ok(teacher,"POST","/teacher/assessments/"+assessmentId+"/questions",Map.of("prompt","2+2?","kind","OBJETIVA","points",2,"alternatives",List.of(Map.of("label","4","correct",true),Map.of("label","5","correct",false)))));
        essayId=uuid(ok(teacher,"POST","/teacher/assessments/"+assessmentId+"/questions",Map.of("prompt","Explique","kind","DISCURSIVA","points",3,"alternatives",List.of())));
        correctId=db.queryForObject("SELECT id FROM alternative WHERE question_id=? AND correct",UUID.class,questionId);
    }
    Map session(String action,int max) {
        var s=ok(teacher,"POST","/teacher/sessions",Map.of("assessmentId",assessmentId,"classId",classId,"startsAt",Instant.now().minusSeconds(60).toString(),"endsAt",Instant.now().plusSeconds(3600).toString(),"durationMinutes",30,"maxViolations",max,"violationAction",action));
        ok(teacher,"POST","/teacher/sessions/"+s.get("id")+"/publish",Map.of());
        return s;
    }
    Map join(Map session) { return ok(student,"POST","/student/join",Map.of("code",session.get("code"))); }
    @Test void verticalFlowAndAuthorization() {
        Map s=session("REGISTRAR",3), a=join(s);String id=a.get("id").toString();
        assertThat(join(s).get("id")).isEqualTo(id);
        assertThat(a.toString()).doesNotContain("correct=");
        assertThat(request(otherStudent,"POST","/student/join",Map.of("code",s.get("code"))).getStatusCode().value()).isEqualTo(403);
        assertThat(request(otherStudent,"GET","/student/attempts/"+id,null).getStatusCode().value()).isEqualTo(404);
        assertThat(request(otherTeacher,"GET","/teacher/attempts/"+id,null).getStatusCode().value()).isEqualTo(404);
        assertThat(request(student,"POST","/teacher/classes",Map.of("name","X")).getStatusCode().value()).isEqualTo(403);
        ok(student,"PUT","/student/attempts/"+id+"/answers/"+questionId,Map.of("alternativeId",correctId));
        ok(student,"PUT","/student/attempts/"+id+"/answers/"+essayId,Map.of("text","Resposta discursiva"));
        Map result=ok(student,"POST","/student/attempts/"+id+"/submit",Map.of());
        assertThat(result.get("status")).isEqualTo("FINALIZADA");
        assertThat(((Number)((Map)result.get("score")).get("total")).doubleValue()).isEqualTo(2);
        assertThat(((Number)((Map)result.get("score")).get("pending")).intValue()).isEqualTo(1);
        assertThat(ok(student,"PUT","/student/attempts/"+id+"/answers/"+essayId,Map.of("text","Não pode")).get("accepted")).isEqualTo(false);
        ok(teacher,"PUT","/teacher/attempts/"+id+"/grades/"+essayId,Map.of("score",2.5,"feedback","Bom trabalho"));
        result=ok(student,"GET","/student/attempts/"+id,null);
        assertThat(((Number)((Map)result.get("score")).get("total")).doubleValue()).isEqualTo(4.5);
        assertThat(((Number)((Map)result.get("score")).get("pending")).intValue()).isZero();
        ok(student,"POST","/student/attempts/"+id+"/submit",Map.of());
        assertThat(db.queryForObject("SELECT score FROM answer WHERE attempt_id=? AND question_id=?",Double.class,UUID.fromString(id),essayId)).isEqualTo(2.5);
    }
    @Test void occurrencesAreIdempotentAndEnforceLimit() {
        Map a=join(session("FINALIZAR",2));String path="/student/attempts/"+a.get("id");
        var event=Map.of("id",UUID.randomUUID(),"kind","ABA_OCULTA");
        ok(student,"POST",path+"/occurrences",event);ok(student,"POST",path+"/occurrences",event);
        ok(student,"POST",path+"/occurrences",Map.of("id",UUID.randomUUID(),"kind","FOCO_PERDIDO"));
        assertThat(db.queryForObject("SELECT count(*) FROM occurrence WHERE attempt_id=? AND counted",Integer.class,uuid(a))).isEqualTo(1);
        db.update("UPDATE occurrence SET created_at=clock_timestamp()-interval '3 seconds' WHERE attempt_id=?",uuid(a));
        Map r=ok(student,"POST",path+"/occurrences",Map.of("id",UUID.randomUUID(),"kind","SAIDA_FULLSCREEN"));
        assertThat(r.get("status")).isEqualTo("FINALIZADA");
        assertThat(ok(student,"GET",path,null).get("finish_reason")).isEqualTo("LIMITE_OCORRENCIAS");
    }
    @Test void deadlineEnforcedOnSaveAndByScheduler() {
        Map a=join(session("REGISTRAR",3));UUID id=uuid(a);
        db.update("UPDATE attempt SET deadline=clock_timestamp()-interval '1 second' WHERE id=?",id);
        assertThat(ok(student,"PUT","/student/attempts/"+id+"/answers/"+questionId,Map.of("alternativeId",correctId)).get("accepted")).isEqualTo(false);
        assertThat(ok(student,"GET","/student/attempts/"+id,null).get("finish_reason")).isEqualTo("TEMPO_ESGOTADO");
        a=join(session("REGISTRAR",3));id=uuid(a);
        db.update("UPDATE attempt SET deadline=clock_timestamp()-interval '1 second' WHERE id=?",id);
        attempts.expire();
        assertThat(db.queryForObject("SELECT status FROM attempt WHERE id=?",String.class,id)).isEqualTo("FINALIZADA");
    }
    @Test void rejectsInvalidQuestionsAndGradesAndFreezesAssessment() {
        assertThat(request(teacher,"POST","/teacher/assessments/"+assessmentId+"/questions",Map.of("prompt","x","kind","OBJETIVA","points",1,"alternatives",List.of(Map.of("label","x","correct",false)))).getStatusCode().value()).isEqualTo(400);
        var sixAlternatives = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> Map.of("label", "Opção " + index, "correct", index == 0)).toList();
        assertThat(request(teacher,"POST","/teacher/assessments/"+assessmentId+"/questions",Map.of("prompt","Limite","kind","OBJETIVA","points",1,"alternatives",sixAlternatives)).getStatusCode().value()).isEqualTo(400);
        var fiveAlternatives = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> Map.of("label", "Opção " + index, "correct", index == 0)).toList();
        assertThat(request(teacher,"POST","/teacher/assessments/"+assessmentId+"/questions",Map.of("prompt","Cinco opções","kind","OBJETIVA","points",1,"alternatives",fiveAlternatives)).getStatusCode().is2xxSuccessful()).isTrue();
        Map a=join(session("REGISTRAR",3));
        assertThat(request(teacher,"POST","/teacher/assessments/"+assessmentId+"/questions",Map.of("prompt","x","kind","DISCURSIVA","points",1,"alternatives",List.of())).getStatusCode().value()).isEqualTo(409);
        ok(student,"POST","/student/attempts/"+a.get("id")+"/submit",Map.of());
        assertThat(request(teacher,"PUT","/teacher/attempts/"+a.get("id")+"/grades/"+essayId,Map.of("score",4)).getStatusCode().value()).isEqualTo(400);
        assertThat(request(teacher,"PUT","/teacher/attempts/"+a.get("id")+"/grades/"+questionId,Map.of("score",1)).getStatusCode().value()).isEqualTo(400);
    }
    @Test void websocketDeliversPrivateUpdatesAndRejectsForeignSubscriptions() throws Exception {
        Map s=session("REGISTRAR",3),a=join(s);
        var client=new org.springframework.web.socket.messaging.WebSocketStompClient(new org.springframework.web.socket.client.standard.StandardWebSocketClient());
        client.setMessageConverter(new org.springframework.messaging.converter.MappingJackson2MessageConverter());
        var headers=new org.springframework.messaging.simp.stomp.StompHeaders();headers.add("Authorization","Bearer "+teacher);
        var denied=new java.util.concurrent.CompletableFuture<Boolean>();
        var connected=client.connectAsync("ws://localhost:"+port+"/ws",new org.springframework.web.socket.WebSocketHttpHeaders(),headers,new org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter() {
            @Override public java.lang.reflect.Type getPayloadType(org.springframework.messaging.simp.stomp.StompHeaders h) { return byte[].class; }
            @Override public void handleFrame(org.springframework.messaging.simp.stomp.StompHeaders h,Object payload) { denied.complete(true); }
            @Override public void handleTransportError(org.springframework.messaging.simp.stomp.StompSession session,Throwable exception) { denied.complete(true); }
        }).get(5,java.util.concurrent.TimeUnit.SECONDS);
        try {
            var received=new java.util.concurrent.CompletableFuture<Map>();
            connected.subscribe("/user/queue/monitor",new org.springframework.messaging.simp.stomp.StompFrameHandler() {
                public java.lang.reflect.Type getPayloadType(org.springframework.messaging.simp.stomp.StompHeaders h) { return Map.class; }
                public void handleFrame(org.springframework.messaging.simp.stomp.StompHeaders h,Object payload) { received.complete((Map)payload); }
            });
            String owner=db.queryForObject("SELECT teacher_id FROM school_class WHERE id=?",UUID.class,classId).toString();
            long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while(registry.findSubscriptions(sub -> sub.getSession().getUser().getName().equals(owner)).isEmpty() && System.nanoTime()<until) Thread.sleep(10);
            assertThat(registry.findSubscriptions(sub -> sub.getSession().getUser().getName().equals(owner))).isNotEmpty();
            ok(student,"PUT","/student/attempts/"+a.get("id")+"/answers/"+essayId,Map.of("text","Ao vivo"));
            assertThat(received.get(5,java.util.concurrent.TimeUnit.SECONDS).get("sessionId")).isEqualTo(s.get("id"));
            connected.subscribe("/queue/another-teacher",new org.springframework.messaging.simp.stomp.StompFrameHandler() {
                public java.lang.reflect.Type getPayloadType(org.springframework.messaging.simp.stomp.StompHeaders h) { return byte[].class; }
                public void handleFrame(org.springframework.messaging.simp.stomp.StompHeaders h,Object payload) { fail("Assinatura indevida aceita"); }
            });
            assertThat(denied.get(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally { if(connected.isConnected()) connected.disconnect();client.stop(); }
    }
}
