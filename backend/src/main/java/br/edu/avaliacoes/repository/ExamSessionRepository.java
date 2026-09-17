package br.edu.avaliacoes.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.edu.avaliacoes.api.domain.dto.request.CreateExamSessionRequest;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ExamSessionRepository extends JdbcRepositorySupport {
    public ExamSessionRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public void requireOwned(UUID sessionId, UUID teacherId) {
        one("SELECT s.id FROM exam_session s JOIN assessment a ON a.id=s.assessment_id " +
                "WHERE s.id=? AND a.teacher_id=?", sessionId, teacherId);
    }

    public Map<String, Object> findByCodeForUpdate(String code) {
        return one("SELECT * FROM exam_session WHERE code=? FOR UPDATE", code);
    }

    public Map<String, Object> findById(UUID sessionId) {
        return one("SELECT * FROM exam_session WHERE id=?", sessionId);
    }

    public Map<String, Object> findWithAssessment(UUID sessionId) {
        return one("SELECT s.*,a.title FROM exam_session s JOIN assessment a ON a.id=s.assessment_id WHERE s.id=?",
                sessionId);
    }

    public List<Map<String, Object>> findByTeacher(UUID teacherId) {
        return rows("SELECT s.*,a.title,c.name AS class_name FROM exam_session s " +
                "JOIN assessment a ON a.id=s.assessment_id JOIN school_class c ON c.id=s.class_id " +
                "WHERE a.teacher_id=? ORDER BY s.starts_at DESC", teacherId);
    }

    public void create(UUID id, String code, CreateExamSessionRequest input) {
        update("INSERT INTO exam_session(id,assessment_id,class_id,code,starts_at,ends_at,duration_minutes," +
                        "max_violations,violation_action) VALUES(?,?,?,?,?,?,?,?,?)",
                id, input.assessmentId(), input.classId(), code, Timestamp.from(input.startsAt()),
                Timestamp.from(input.endsAt()), input.durationMinutes(), input.maxViolations(), input.violationAction());
    }

    public void publish(UUID sessionId) {
        update("UPDATE exam_session SET status='PUBLICADA' WHERE id=?", sessionId);
    }

    public List<Map<String, Object>> monitor(UUID sessionId) {
        return rows("SELECT u.id AS student_id,u.name,t.id,t.status,t.deadline,t.last_seen,t.finish_reason," +
                "(SELECT count(*) FROM occurrence o WHERE o.attempt_id=t.id AND o.counted) AS violations," +
                "(SELECT count(*) FROM answer r WHERE r.attempt_id=t.id AND " +
                "(r.alternative_id IS NOT NULL OR COALESCE(trim(r.text_value),'')<>'')) AS answered " +
                "FROM enrollment e JOIN app_user u ON u.id=e.student_id " +
                "LEFT JOIN attempt t ON t.student_id=u.id AND t.session_id=? " +
                "WHERE e.class_id=(SELECT class_id FROM exam_session WHERE id=?) ORDER BY u.name",
                sessionId, sessionId);
    }

    public UUID findTeacherId(UUID sessionId) {
        return (UUID) one("SELECT a.teacher_id FROM exam_session s JOIN assessment a ON a.id=s.assessment_id " +
                "WHERE s.id=?", sessionId).get("teacher_id");
    }
}
