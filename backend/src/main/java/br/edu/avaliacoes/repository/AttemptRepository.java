package br.edu.avaliacoes.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AttemptRepository extends JdbcRepositorySupport {
    public AttemptRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public Map<String, Object> lock(UUID attemptId) {
        return one("SELECT * FROM attempt WHERE id=? FOR UPDATE", attemptId);
    }

    public Optional<Map<String, Object>> find(UUID sessionId, UUID studentId) {
        return rows("SELECT * FROM attempt WHERE session_id=? AND student_id=?", sessionId, studentId)
                .stream().findFirst();
    }

    public Instant currentTime() {
        return (Instant) one("SELECT clock_timestamp() AS now").get("now");
    }

    public void create(UUID id, UUID sessionId, UUID studentId, Instant startedAt, Instant deadline) {
        update("INSERT INTO attempt(id,session_id,student_id,started_at,deadline,last_seen) VALUES(?,?,?,?,?,?)",
                id, sessionId, studentId, Timestamp.from(startedAt), Timestamp.from(deadline), Timestamp.from(startedAt));
    }

    public List<Map<String, Object>> history(UUID studentId) {
        return rows("SELECT t.id,t.status,t.deadline,a.title FROM attempt t " +
                "JOIN exam_session s ON s.id=t.session_id JOIN assessment a ON a.id=s.assessment_id " +
                "WHERE t.student_id=? ORDER BY t.started_at DESC", studentId);
    }

    public List<Map<String, Object>> answers(UUID attemptId) {
        return rows("SELECT r.*,q.position,q.prompt,q.kind FROM answer r JOIN question q ON q.id=r.question_id " +
                "WHERE r.attempt_id=? ORDER BY q.position", attemptId);
    }

    public List<Map<String, Object>> occurrences(UUID attemptId) {
        return rows("SELECT * FROM occurrence WHERE attempt_id=? ORDER BY created_at", attemptId);
    }

    public long countedViolations(UUID attemptId) {
        return count("SELECT count(*) FROM occurrence WHERE attempt_id=? AND counted", attemptId);
    }

    public Map<String, Object> score(UUID attemptId) {
        return one("SELECT COALESCE(sum(score),0) AS total,count(*) FILTER(WHERE score IS NULL) AS pending " +
                "FROM answer WHERE attempt_id=?", attemptId);
    }

    public void saveAnswer(UUID attemptId, UUID questionId, UUID alternativeId, String text) {
        update("INSERT INTO answer(attempt_id,question_id,alternative_id,text_value) VALUES(?,?,?,?) " +
                        "ON CONFLICT(attempt_id,question_id) DO UPDATE SET alternative_id=EXCLUDED.alternative_id," +
                        "text_value=EXCLUDED.text_value,updated_at=clock_timestamp()",
                attemptId, questionId, alternativeId, text);
    }

    public void touch(UUID attemptId) {
        update("UPDATE attempt SET last_seen=clock_timestamp() WHERE id=?", attemptId);
    }

    public boolean hasRecentCountedOccurrence(UUID attemptId) {
        return count("SELECT count(*) FROM occurrence WHERE attempt_id=? AND counted " +
                "AND created_at > clock_timestamp()-interval '2 seconds'", attemptId) > 0;
    }

    public void addOccurrence(UUID occurrenceId, UUID attemptId, String kind, boolean counted) {
        update("INSERT INTO occurrence(id,attempt_id,kind,counted) VALUES(?,?,?,?) ON CONFLICT(id) DO NOTHING",
                occurrenceId, attemptId, kind, counted);
    }

    public void initializeAnswers(UUID attemptId, UUID sessionId) {
        update("INSERT INTO answer(attempt_id,question_id) SELECT ?,q.id FROM question q " +
                "JOIN exam_session s ON s.assessment_id=q.assessment_id WHERE s.id=? ON CONFLICT DO NOTHING",
                attemptId, sessionId);
    }

    public void calculateAutomaticScores(UUID attemptId) {
        update("UPDATE answer r SET score=CASE WHEN q.kind='OBJETIVA' THEN " +
                "CASE WHEN EXISTS(SELECT 1 FROM alternative alt WHERE alt.id=r.alternative_id AND alt.correct) " +
                "THEN q.points ELSE 0 END WHEN COALESCE(trim(r.text_value),'')='' THEN 0 ELSE NULL END " +
                "FROM question q WHERE q.id=r.question_id AND r.attempt_id=?", attemptId);
    }

    public void finish(UUID attemptId, String reason) {
        update("UPDATE attempt SET status='FINALIZADA',submitted_at=clock_timestamp(),finish_reason=? WHERE id=?",
                reason, attemptId);
    }

    public List<Map<String, Object>> expiredActiveAttempts() {
        return rows("SELECT * FROM attempt WHERE status='EM_ANDAMENTO' AND deadline<=clock_timestamp() " +
                "FOR UPDATE SKIP LOCKED");
    }

    public void grade(UUID attemptId, UUID questionId, BigDecimal score, String feedback) {
        update("UPDATE answer SET score=?,feedback=? WHERE attempt_id=? AND question_id=?",
                score, feedback, attemptId, questionId);
    }
}
