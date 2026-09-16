package br.edu.avaliacoes.service;

import br.edu.avaliacoes.api.Inputs.*;
import java.util.*;
import java.sql.Timestamp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class TeacherService {
    private final Store db; private final PasswordEncoder passwords;
    public TeacherService(Store db,PasswordEncoder passwords) { this.db=db; this.passwords=passwords; }
    public Map<String,Object> ownedClass(UUID id,UUID teacher) { return db.one("SELECT * FROM school_class WHERE id=? AND teacher_id=?",id,teacher); }
    public Map<String,Object> ownedAssessment(UUID id,UUID teacher) { return db.one("SELECT * FROM assessment WHERE id=? AND teacher_id=? FOR UPDATE",id,teacher); }
    public Map<String,Object> ownedSession(UUID id,UUID teacher) { return db.one("SELECT s.* FROM exam_session s JOIN assessment a ON a.id=s.assessment_id WHERE s.id=? AND a.teacher_id=?",id,teacher); }
    public List<Map<String,Object>> users() { return db.rows("SELECT id,name,email,role FROM app_user ORDER BY name"); }
    public Map<String,Object> user(User input) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO app_user(id,name,email,password_hash,role) VALUES(?,?,?,?,?)",id,input.name().trim(),input.email().trim().toLowerCase(Locale.ROOT),passwords.encode(input.password()),input.role());
        return Map.of("id",id);
    }
    public List<Map<String,Object>> classes(UUID teacher) { return db.rows("SELECT c.*, (SELECT count(*) FROM enrollment e WHERE e.class_id=c.id) AS students FROM school_class c WHERE teacher_id=? ORDER BY name",teacher); }
    public Map<String,Object> createClass(UUID teacher,Named input) {
        UUID id=UUID.randomUUID(); db.update("INSERT INTO school_class VALUES(?,?,?)",id,input.name(),teacher); return Map.of("id",id);
    }
    public List<Map<String,Object>> students(UUID classId,UUID teacher) {
        ownedClass(classId,teacher);
        return db.rows("SELECT u.id,u.name,u.email FROM enrollment e JOIN app_user u ON u.id=e.student_id WHERE e.class_id=? ORDER BY u.name",classId);
    }
    @Transactional public Map<String,Object> createStudent(UUID classId,UUID teacher,User input) {
        ownedClass(classId,teacher);
        if(!input.role().equals("ALUNO")) throw new ResponseStatusException(BAD_REQUEST,"Somente alunos");
        var created=user(input); enroll(classId,teacher,(UUID)created.get("id")); return created;
    }
    public void enroll(UUID classId,UUID teacher,UUID student) {
        ownedClass(classId,teacher); db.one("SELECT id FROM app_user WHERE id=? AND role='ALUNO'",student);
        db.update("INSERT INTO enrollment VALUES(?,?) ON CONFLICT DO NOTHING",classId,student);
    }
    public List<Map<String,Object>> assessments(UUID teacher) { return db.rows("SELECT a.*,EXISTS(SELECT 1 FROM exam_session s WHERE s.assessment_id=a.id) AS locked FROM assessment a WHERE teacher_id=? ORDER BY title",teacher); }
    public Map<String,Object> createAssessment(UUID teacher,Assessment input) {
        UUID id=UUID.randomUUID(); db.update("INSERT INTO assessment VALUES(?,?,?)",id,input.title(),teacher); return Map.of("id",id);
    }
    public List<Map<String,Object>> questions(UUID assessmentId,boolean keys) {
        var questions=db.rows("SELECT * FROM question WHERE assessment_id=? ORDER BY position",assessmentId);
        for(var q:questions) q.put("alternatives",db.rows("SELECT id,label,position"+(keys?",correct":"")+" FROM alternative WHERE question_id=? ORDER BY position",q.get("id")));
        return questions;
    }
    @Transactional public Map<String,Object> addQuestion(UUID id,UUID teacher,Question input) {
        ownedAssessment(id,teacher);
        if(db.count("SELECT count(*) FROM exam_session WHERE assessment_id=?",id)>0) throw new ResponseStatusException(CONFLICT,"Avaliação congelada: já possui aplicação");
        if(input.kind().equals("OBJETIVA") && (input.alternatives().size()<2 || input.alternatives().stream().filter(Alternative::correct).count()!=1)) throw new ResponseStatusException(BAD_REQUEST,"Informe ao menos duas alternativas e um único gabarito");
        if(input.kind().equals("DISCURSIVA") && !input.alternatives().isEmpty()) throw new ResponseStatusException(BAD_REQUEST,"Discursiva não possui alternativas");
        UUID q=UUID.randomUUID();
        int pos=(int)db.count("SELECT count(*) FROM question WHERE assessment_id=?",id)+1;
        db.update("INSERT INTO question VALUES(?,?,?,?,?,?)",q,id,input.prompt(),input.kind(),input.points(),pos);
        int i=0;
        for(var alt:input.alternatives()) db.update("INSERT INTO alternative VALUES(?,?,?,?,?)",UUID.randomUUID(),q,alt.label(),alt.correct(),++i);
        return Map.of("id",q);
    }
    @Transactional public Map<String,Object> createSession(UUID teacher,Session input) {
        ownedAssessment(input.assessmentId(),teacher); ownedClass(input.classId(),teacher);
        if(!input.endsAt().isAfter(input.startsAt())) throw new ResponseStatusException(BAD_REQUEST,"Fim deve ser posterior ao início");
        if(db.count("SELECT count(*) FROM question WHERE assessment_id=?",input.assessmentId())==0) throw new ResponseStatusException(BAD_REQUEST,"Adicione questões primeiro");
        UUID id=UUID.randomUUID();
        String code=UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);
        db.update("INSERT INTO exam_session(id,assessment_id,class_id,code,starts_at,ends_at,duration_minutes,max_violations,violation_action) VALUES(?,?,?,?,?,?,?,?,?)",id,input.assessmentId(),input.classId(),code,Timestamp.from(input.startsAt()),Timestamp.from(input.endsAt()),input.durationMinutes(),input.maxViolations(),input.violationAction());
        return Map.of("id",id,"code",code);
    }
    public void publish(UUID id,UUID teacher) { ownedSession(id,teacher); db.update("UPDATE exam_session SET status='PUBLICADA' WHERE id=?",id); }
    public List<Map<String,Object>> sessions(UUID teacher) {
        return db.rows("SELECT s.*,a.title,c.name AS class_name FROM exam_session s JOIN assessment a ON a.id=s.assessment_id JOIN school_class c ON c.id=s.class_id WHERE a.teacher_id=? ORDER BY s.starts_at DESC",teacher);
    }
    public List<Map<String,Object>> monitor(UUID id,UUID teacher) {
        ownedSession(id,teacher);
        return db.rows("SELECT u.id AS student_id,u.name,t.id,t.status,t.deadline,t.last_seen,t.finish_reason,(SELECT count(*) FROM occurrence o WHERE o.attempt_id=t.id AND o.counted) AS violations,(SELECT count(*) FROM answer r WHERE r.attempt_id=t.id AND (r.alternative_id IS NOT NULL OR COALESCE(trim(r.text_value),'')<>'')) AS answered FROM enrollment e JOIN app_user u ON u.id=e.student_id LEFT JOIN attempt t ON t.student_id=u.id AND t.session_id=? WHERE e.class_id=(SELECT class_id FROM exam_session WHERE id=?) ORDER BY u.name",id,id);
    }
}
