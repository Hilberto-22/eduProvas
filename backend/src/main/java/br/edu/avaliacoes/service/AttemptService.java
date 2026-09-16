package br.edu.avaliacoes.service;

import br.edu.avaliacoes.api.Inputs.*;
import br.edu.avaliacoes.realtime.LiveEvents;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class AttemptService {
    private final Store db; private final TeacherService teachers; private final LiveEvents live;
    public AttemptService(Store db,TeacherService teachers,LiveEvents live) { this.db=db; this.teachers=teachers; this.live=live; }
    private Map<String,Object> lock(UUID id) { return db.one("SELECT * FROM attempt WHERE id=? FOR UPDATE",id); }
    private void owner(Map<String,Object> a,UUID student) {
        if(!a.get("student_id").equals(student)) throw new ResponseStatusException(NOT_FOUND,"Tentativa não encontrada");
    }
    private Instant now() { return (Instant)db.one("SELECT clock_timestamp() AS now").get("now"); }
    private boolean active(Map<String,Object> a) {
        if(!a.get("status").equals("EM_ANDAMENTO")) return false;
        if(!now().isBefore((Instant)a.get("deadline"))) { finish(a,"TEMPO_ESGOTADO"); return false; }
        return true;
    }
    @Transactional public Map<String,Object> join(UUID student,String code) {
        var s=db.one("SELECT * FROM exam_session WHERE code=? FOR UPDATE",code.trim().toUpperCase(Locale.ROOT));
        UUID session=(UUID)s.get("id");
        if(db.count("SELECT count(*) FROM enrollment WHERE class_id=? AND student_id=?",s.get("class_id"),student)==0) throw new ResponseStatusException(FORBIDDEN,"Você não está matriculado nesta turma");
        var existing=db.rows("SELECT * FROM attempt WHERE session_id=? AND student_id=?",session,student);
        if(!existing.isEmpty()) return read((UUID)existing.getFirst().get("id"),student);
        Instant time=now();
        if(!s.get("status").equals("PUBLICADA") || time.isBefore((Instant)s.get("starts_at")) || !time.isBefore((Instant)s.get("ends_at"))) throw new ResponseStatusException(CONFLICT,"Aplicação fora do período de realização");
        UUID id=UUID.randomUUID();
        Instant deadline=time.plusSeconds(((Number)s.get("duration_minutes")).longValue()*60);
        if(deadline.isAfter((Instant)s.get("ends_at"))) deadline=(Instant)s.get("ends_at");
        db.update("INSERT INTO attempt(id,session_id,student_id,started_at,deadline,last_seen) VALUES(?,?,?,?,?,?)",id,session,student,Timestamp.from(time),Timestamp.from(deadline),Timestamp.from(time));
        live.changed(session); return snapshot(lock(id),false);
    }
    @Transactional public Map<String,Object> read(UUID id,UUID student) {
        var a=lock(id); owner(a,student); active(a);
        db.update("UPDATE attempt SET last_seen=clock_timestamp() WHERE id=?",id);
        return snapshot(lock(id),false);
    }
    public List<Map<String,Object>> history(UUID student) {
        return db.rows("SELECT t.id,t.status,t.deadline,a.title FROM attempt t JOIN exam_session s ON s.id=t.session_id JOIN assessment a ON a.id=s.assessment_id WHERE t.student_id=? ORDER BY t.started_at DESC",student);
    }
    private Map<String,Object> snapshot(Map<String,Object> a,boolean teacher) {
        var s=db.one("SELECT s.*,a.title FROM exam_session s JOIN assessment a ON a.id=s.assessment_id WHERE s.id=?",a.get("session_id"));
        a.put("session",s); a.put("server_now",now());
        a.put("questions",teachers.questions((UUID)s.get("assessment_id"),teacher));
        a.put("answers",db.rows("SELECT r.*,q.position,q.prompt,q.kind FROM answer r JOIN question q ON q.id=r.question_id WHERE r.attempt_id=? ORDER BY q.position",a.get("id")));
        a.put("occurrences",db.rows("SELECT * FROM occurrence WHERE attempt_id=? ORDER BY created_at",a.get("id")));
        a.put("violations",db.count("SELECT count(*) FROM occurrence WHERE attempt_id=? AND counted",a.get("id")));
        a.put("score",db.one("SELECT COALESCE(sum(score),0) AS total,count(*) FILTER(WHERE score IS NULL) AS pending FROM answer WHERE attempt_id=?",a.get("id")));
        a.put("max_score",db.one("SELECT COALESCE(sum(points),0) AS total FROM question WHERE assessment_id=?",s.get("assessment_id")).get("total"));
        return a;
    }
    @Transactional public Map<String,Object> save(UUID id,UUID student,UUID question,Answer input) {
        var a=lock(id); owner(a,student);
        if(!active(a)) return Map.of("accepted",false,"status","FINALIZADA");
        var q=db.one("SELECT q.* FROM question q JOIN exam_session s ON s.assessment_id=q.assessment_id WHERE s.id=? AND q.id=?",a.get("session_id"),question);
        if(q.get("kind").equals("OBJETIVA")) {
            if(input.text()!=null && !input.text().isBlank()) throw new ResponseStatusException(BAD_REQUEST,"Questão objetiva não aceita texto");
            if(input.alternativeId()!=null) db.one("SELECT id FROM alternative WHERE id=? AND question_id=?",input.alternativeId(),question);
        } else if(input.alternativeId()!=null) throw new ResponseStatusException(BAD_REQUEST,"Discursiva não aceita alternativa");
        db.update("INSERT INTO answer(attempt_id,question_id,alternative_id,text_value) VALUES(?,?,?,?) ON CONFLICT(attempt_id,question_id) DO UPDATE SET alternative_id=EXCLUDED.alternative_id,text_value=EXCLUDED.text_value,updated_at=clock_timestamp()",id,question,input.alternativeId(),input.text());
        db.update("UPDATE attempt SET last_seen=clock_timestamp() WHERE id=?",id);
        live.changed((UUID)a.get("session_id")); return Map.of("accepted",true,"saved_at",now());
    }
    @Transactional public Map<String,Object> occurrence(UUID id,UUID student,Occurrence input) {
        var a=lock(id); owner(a,student);
        if(active(a)) {
            boolean counted=db.count("SELECT count(*) FROM occurrence WHERE attempt_id=? AND counted AND created_at > clock_timestamp()-interval '2 seconds'",id)==0;
            db.update("INSERT INTO occurrence(id,attempt_id,kind,counted) VALUES(?,?,?,?) ON CONFLICT(id) DO NOTHING",input.id(),id,input.kind(),counted);
            var s=db.one("SELECT * FROM exam_session WHERE id=?",a.get("session_id"));
            long count=db.count("SELECT count(*) FROM occurrence WHERE attempt_id=? AND counted",id);
            if(s.get("violation_action").equals("FINALIZAR") && count>=((Number)s.get("max_violations")).intValue()) finish(a,"LIMITE_OCORRENCIAS");
            live.changed((UUID)a.get("session_id"));
        }
        return Map.of("status",lock(id).get("status"),"violations",db.count("SELECT count(*) FROM occurrence WHERE attempt_id=? AND counted",id));
    }
    @Transactional public Map<String,Object> submit(UUID id,UUID student) {
        var a=lock(id); owner(a,student);
        if(active(a)) finish(a,"ENTREGA_ALUNO");
        return snapshot(lock(id),false);
    }
    private void finish(Map<String,Object> a,String reason) {
        UUID id=(UUID)a.get("id");
        db.update("INSERT INTO answer(attempt_id,question_id) SELECT ?,q.id FROM question q JOIN exam_session s ON s.assessment_id=q.assessment_id WHERE s.id=? ON CONFLICT DO NOTHING",id,a.get("session_id"));
        db.update("UPDATE answer r SET score=CASE WHEN q.kind='OBJETIVA' THEN CASE WHEN EXISTS(SELECT 1 FROM alternative alt WHERE alt.id=r.alternative_id AND alt.correct) THEN q.points ELSE 0 END WHEN COALESCE(trim(r.text_value),'')='' THEN 0 ELSE NULL END FROM question q WHERE q.id=r.question_id AND r.attempt_id=?",id);
        db.update("UPDATE attempt SET status='FINALIZADA',submitted_at=clock_timestamp(),finish_reason=? WHERE id=?",reason,id);
        a.put("status","FINALIZADA"); live.changed((UUID)a.get("session_id"));
    }
    @Scheduled(fixedDelay=5000) @Transactional public void expire() {
        for(var a:db.rows("SELECT * FROM attempt WHERE status='EM_ANDAMENTO' AND deadline<=clock_timestamp() FOR UPDATE SKIP LOCKED")) finish(a,"TEMPO_ESGOTADO");
    }
    @Transactional public Map<String,Object> review(UUID id,UUID teacher) {
        var a=lock(id); teachers.ownedSession((UUID)a.get("session_id"),teacher); active(a); return snapshot(lock(id),true);
    }
    @Transactional public void grade(UUID id,UUID teacher,UUID question,Grade input) {
        var a=lock(id); teachers.ownedSession((UUID)a.get("session_id"),teacher);
        if(!a.get("status").equals("FINALIZADA")) throw new ResponseStatusException(CONFLICT,"Aguarde a finalização");
        var q=db.one("SELECT q.* FROM question q JOIN exam_session s ON s.assessment_id=q.assessment_id WHERE s.id=? AND q.id=?",a.get("session_id"),question);
        if(!q.get("kind").equals("DISCURSIVA") || input.score().compareTo((BigDecimal)q.get("points"))>0) throw new ResponseStatusException(BAD_REQUEST,"Nota fora do intervalo ou questão objetiva");
        db.update("UPDATE answer SET score=?,feedback=? WHERE attempt_id=? AND question_id=?",input.score(),input.feedback(),id,question);
        live.changed((UUID)a.get("session_id"));
    }
}
