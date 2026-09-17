package br.edu.avaliacoes.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AssessmentRepository extends JdbcRepositorySupport {
    public AssessmentRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public void requireOwnedForUpdate(UUID assessmentId, UUID teacherId) {
        one("SELECT id FROM assessment WHERE id=? AND teacher_id=? FOR UPDATE", assessmentId, teacherId);
    }

    public void requireOwned(UUID assessmentId, UUID teacherId) {
        one("SELECT id FROM assessment WHERE id=? AND teacher_id=?", assessmentId, teacherId);
    }

    public List<Map<String, Object>> findByTeacher(UUID teacherId) {
        return rows("SELECT a.*,EXISTS(SELECT 1 FROM exam_session s WHERE s.assessment_id=a.id) AS locked " +
                "FROM assessment a WHERE teacher_id=? ORDER BY title", teacherId);
    }

    public void create(UUID id, String title, UUID teacherId) {
        update("INSERT INTO assessment(id,title,teacher_id) VALUES(?,?,?)", id, title, teacherId);
    }

    public List<Map<String, Object>> findQuestions(UUID assessmentId, boolean includeAnswerKey) {
        var questions = rows("SELECT * FROM question WHERE assessment_id=? ORDER BY position", assessmentId);
        for (var question : questions) {
            String answerKeyColumn = includeAnswerKey ? ",correct" : "";
            question.put("alternatives", rows("SELECT id,label,position" + answerKeyColumn +
                    " FROM alternative WHERE question_id=? ORDER BY position", question.get("id")));
        }
        return questions;
    }

    public boolean hasSessions(UUID assessmentId) {
        return count("SELECT count(*) FROM exam_session WHERE assessment_id=?", assessmentId) > 0;
    }

    public int nextQuestionPosition(UUID assessmentId) {
        return Math.toIntExact(count("SELECT count(*) FROM question WHERE assessment_id=?", assessmentId) + 1);
    }

    public void createQuestion(UUID id, UUID assessmentId, String prompt, String kind,
                               BigDecimal points, int position) {
        update("INSERT INTO question(id,assessment_id,prompt,kind,points,position) VALUES(?,?,?,?,?,?)",
                id, assessmentId, prompt, kind, points, position);
    }

    public void createAlternative(UUID id, UUID questionId, String label, boolean correct, int position) {
        update("INSERT INTO alternative(id,question_id,label,correct,position) VALUES(?,?,?,?,?)",
                id, questionId, label, correct, position);
    }

    public boolean hasQuestions(UUID assessmentId) {
        return count("SELECT count(*) FROM question WHERE assessment_id=?", assessmentId) > 0;
    }

    public Map<String, Object> findQuestionInSession(UUID sessionId, UUID questionId) {
        return one("SELECT q.* FROM question q JOIN exam_session s ON s.assessment_id=q.assessment_id " +
                "WHERE s.id=? AND q.id=?", sessionId, questionId);
    }

    public void requireAlternative(UUID questionId, UUID alternativeId) {
        one("SELECT id FROM alternative WHERE id=? AND question_id=?", alternativeId, questionId);
    }

    public Object maximumScore(UUID assessmentId) {
        return one("SELECT COALESCE(sum(points),0) AS total FROM question WHERE assessment_id=?", assessmentId)
                .get("total");
    }
}
