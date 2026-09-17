package br.edu.avaliacoes.service.impl;

import br.edu.avaliacoes.api.domain.dto.request.GradeAnswerRequest;
import br.edu.avaliacoes.api.domain.dto.request.OccurrenceRequest;
import br.edu.avaliacoes.api.domain.dto.request.SaveAnswerRequest;
import br.edu.avaliacoes.realtime.LiveEvents;
import br.edu.avaliacoes.repository.*;
import br.edu.avaliacoes.service.AttemptService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
public class AttemptServiceImpl implements AttemptService {
    private final AttemptRepository attempts;
    private final SchoolClassRepository classes;
    private final AssessmentRepository assessments;
    private final ExamSessionRepository sessions;
    private final LiveEvents liveEvents;

    public AttemptServiceImpl(AttemptRepository attempts, SchoolClassRepository classes,
                              AssessmentRepository assessments, ExamSessionRepository sessions,
                              LiveEvents liveEvents) {
        this.attempts = attempts;
        this.classes = classes;
        this.assessments = assessments;
        this.sessions = sessions;
        this.liveEvents = liveEvents;
    }

    @Override
    @Transactional
    public Map<String, Object> join(UUID studentId, String code) {
        var session = sessions.findByCodeForUpdate(code.trim().toUpperCase(Locale.ROOT));
        UUID sessionId = id(session, "id");
        if (!classes.isEnrolled(id(session, "class_id"), studentId)) {
            throw new ResponseStatusException(FORBIDDEN, "Você não está matriculado nesta turma");
        }

        var existing = attempts.find(sessionId, studentId);
        if (existing.isPresent()) {
            return read(id(existing.get(), "id"), studentId);
        }

        Instant now = attempts.currentTime();
        if (!"PUBLICADA".equals(session.get("status")) ||
                now.isBefore((Instant) session.get("starts_at")) ||
                !now.isBefore((Instant) session.get("ends_at"))) {
            throw new ResponseStatusException(CONFLICT, "Aplicação fora do período de realização");
        }

        UUID attemptId = UUID.randomUUID();
        Instant deadline = calculateDeadline(now, session);
        attempts.create(attemptId, sessionId, studentId, now, deadline);
        liveEvents.changed(sessionId);
        return snapshot(attempts.lock(attemptId), false);
    }

    @Override
    @Transactional
    public Map<String, Object> read(UUID attemptId, UUID studentId) {
        var attempt = attempts.lock(attemptId);
        requireOwner(attempt, studentId);
        ensureActiveOrFinish(attempt);
        attempts.touch(attemptId);
        return snapshot(attempts.lock(attemptId), false);
    }

    @Override
    public List<Map<String, Object>> history(UUID studentId) {
        return attempts.history(studentId);
    }

    @Override
    @Transactional
    public Map<String, Object> save(UUID attemptId, UUID studentId, UUID questionId, SaveAnswerRequest input) {
        var attempt = attempts.lock(attemptId);
        requireOwner(attempt, studentId);
        if (!ensureActiveOrFinish(attempt)) {
            return Map.of("accepted", false, "status", "FINALIZADA");
        }

        var question = assessments.findQuestionInSession(id(attempt, "session_id"), questionId);
        validateAnswer(question, questionId, input);
        attempts.saveAnswer(attemptId, questionId, input.alternativeId(), input.text());
        attempts.touch(attemptId);
        liveEvents.changed(id(attempt, "session_id"));
        return Map.of("accepted", true, "saved_at", attempts.currentTime());
    }

    @Override
    @Transactional
    public Map<String, Object> occurrence(UUID attemptId, UUID studentId, OccurrenceRequest input) {
        var attempt = attempts.lock(attemptId);
        requireOwner(attempt, studentId);
        if (ensureActiveOrFinish(attempt)) {
            boolean counted = !attempts.hasRecentCountedOccurrence(attemptId);
            attempts.addOccurrence(input.id(), attemptId, input.kind(), counted);
            applyViolationPolicy(attempt);
            liveEvents.changed(id(attempt, "session_id"));
        }
        return Map.of(
                "status", attempts.lock(attemptId).get("status"),
                "violations", attempts.countedViolations(attemptId));
    }

    @Override
    @Transactional
    public Map<String, Object> submit(UUID attemptId, UUID studentId) {
        var attempt = attempts.lock(attemptId);
        requireOwner(attempt, studentId);
        if (ensureActiveOrFinish(attempt)) {
            finish(attempt, "ENTREGA_ALUNO");
        }
        return snapshot(attempts.lock(attemptId), false);
    }

    @Override
    @Transactional
    public Map<String, Object> review(UUID attemptId, UUID teacherId) {
        var attempt = attempts.lock(attemptId);
        sessions.requireOwned(id(attempt, "session_id"), teacherId);
        ensureActiveOrFinish(attempt);
        return snapshot(attempts.lock(attemptId), true);
    }

    @Override
    @Transactional
    public void grade(UUID attemptId, UUID teacherId, UUID questionId, GradeAnswerRequest input) {
        var attempt = attempts.lock(attemptId);
        UUID sessionId = id(attempt, "session_id");
        sessions.requireOwned(sessionId, teacherId);
        if (!"FINALIZADA".equals(attempt.get("status"))) {
            throw new ResponseStatusException(CONFLICT, "Aguarde a finalização");
        }

        var question = assessments.findQuestionInSession(sessionId, questionId);
        if (!"DISCURSIVA".equals(question.get("kind")) ||
                input.score().compareTo((BigDecimal) question.get("points")) > 0) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Nota fora do intervalo ou questão objetiva");
        }
        attempts.grade(attemptId, questionId, input.score(), input.feedback());
        liveEvents.changed(sessionId);
    }

    @Override
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void expire() {
        for (var attempt : attempts.expiredActiveAttempts()) {
            finish(attempt, "TEMPO_ESGOTADO");
        }
    }

    private Map<String, Object> snapshot(Map<String, Object> attempt, boolean includeAnswerKey) {
        var session = sessions.findWithAssessment(id(attempt, "session_id"));
        UUID assessmentId = id(session, "assessment_id");
        UUID attemptId = id(attempt, "id");

        attempt.put("session", session);
        attempt.put("server_now", attempts.currentTime());
        attempt.put("questions", assessments.findQuestions(assessmentId, includeAnswerKey));
        attempt.put("answers", attempts.answers(attemptId));
        attempt.put("occurrences", attempts.occurrences(attemptId));
        attempt.put("violations", attempts.countedViolations(attemptId));
        attempt.put("score", attempts.score(attemptId));
        attempt.put("max_score", assessments.maximumScore(assessmentId));
        return attempt;
    }

    private void validateAnswer(Map<String, Object> question, UUID questionId, SaveAnswerRequest input) {
        if ("OBJETIVA".equals(question.get("kind"))) {
            if (input.text() != null && !input.text().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Questão objetiva não aceita texto");
            }
            if (input.alternativeId() != null) {
                assessments.requireAlternative(questionId, input.alternativeId());
            }
            return;
        }
        if (input.alternativeId() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "Questão discursiva não aceita alternativa");
        }
    }

    private void applyViolationPolicy(Map<String, Object> attempt) {
        UUID attemptId = id(attempt, "id");
        var session = sessions.findById(id(attempt, "session_id"));
        long violations = attempts.countedViolations(attemptId);
        int maximum = ((Number) session.get("max_violations")).intValue();
        if ("FINALIZAR".equals(session.get("violation_action")) && violations >= maximum) {
            finish(attempt, "LIMITE_OCORRENCIAS");
        }
    }

    private boolean ensureActiveOrFinish(Map<String, Object> attempt) {
        if (!"EM_ANDAMENTO".equals(attempt.get("status"))) {
            return false;
        }
        if (!attempts.currentTime().isBefore((Instant) attempt.get("deadline"))) {
            finish(attempt, "TEMPO_ESGOTADO");
            return false;
        }
        return true;
    }

    private void finish(Map<String, Object> attempt, String reason) {
        UUID attemptId = id(attempt, "id");
        UUID sessionId = id(attempt, "session_id");
        attempts.initializeAnswers(attemptId, sessionId);
        attempts.calculateAutomaticScores(attemptId);
        attempts.finish(attemptId, reason);
        attempt.put("status", "FINALIZADA");
        liveEvents.changed(sessionId);
    }

    private void requireOwner(Map<String, Object> attempt, UUID studentId) {
        if (!studentId.equals(attempt.get("student_id"))) {
            throw new ResponseStatusException(NOT_FOUND, "Tentativa não encontrada");
        }
    }

    private Instant calculateDeadline(Instant startedAt, Map<String, Object> session) {
        long durationMinutes = ((Number) session.get("duration_minutes")).longValue();
        Instant deadline = startedAt.plusSeconds(durationMinutes * 60);
        Instant sessionEnd = (Instant) session.get("ends_at");
        return deadline.isAfter(sessionEnd) ? sessionEnd : deadline;
    }

    private UUID id(Map<String, Object> source, String field) {
        return (UUID) source.get(field);
    }
}
