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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import br.edu.avaliacoes.api.domain.dto.response.Responses;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import br.edu.avaliacoes.api.domain.dto.request.PageRequest;
import br.edu.avaliacoes.api.domain.dto.response.PageResponse;
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
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;

    public AttemptServiceImpl(AttemptRepository attempts, SchoolClassRepository classes,
                              AssessmentRepository assessments, ExamSessionRepository sessions,
                              LiveEvents liveEvents, PlatformTransactionManager transactionManager) {
        this.attempts = attempts;
        this.classes = classes;
        this.assessments = assessments;
        this.sessions = sessions;
        this.liveEvents = liveEvents;
        this.writes = new TransactionTemplate(transactionManager);
        this.reads = new TransactionTemplate(transactionManager);
        this.reads.setReadOnly(true);
        this.reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public Map<String, Object> join(UUID studentId, String code) {
        UUID attemptId = writes.execute(transaction -> {
            var session = sessions.findByCode(code.trim().toUpperCase(Locale.ROOT));
            UUID sessionId = id(session, "id");
            if (!classes.isEnrolled(id(session, "class_id"), studentId)) {
                throw new ResponseStatusException(FORBIDDEN, "Você não está matriculado nesta turma");
            }
            var existing = attempts.find(sessionId, studentId);
            if (existing.isPresent()) {
                UUID existingId = id(existing.get(), "id");
                ensureActiveOrFinish(attempts.lockOwned(existingId, studentId));
                attempts.touch(existingId);
                return existingId;
            }
            Instant now = attempts.currentTime();
            if (!"PUBLICADA".equals(session.get("status")) ||
                    now.isBefore((Instant) session.get("starts_at")) ||
                    !now.isBefore((Instant) session.get("ends_at"))) {
                throw new ResponseStatusException(CONFLICT, "Aplicação fora do período de realização");
            }
            UUID createdId = UUID.randomUUID();
            // The unique constraint serializes only competing joins by this student/session.
            if (attempts.create(createdId, sessionId, studentId, now, calculateDeadline(now, session))) {
                liveEvents.changed(sessionId);
                return createdId;
            }
            UUID concurrentId = id(attempts.find(sessionId, studentId).orElseThrow(), "id");
            ensureActiveOrFinish(attempts.lockOwned(concurrentId, studentId));
            attempts.touch(concurrentId);
            return concurrentId;
        });
        return studentSnapshot(attemptId, studentId);
    }

    @Override
    public Map<String, Object> read(UUID attemptId, UUID studentId) {
        writes.executeWithoutResult(transaction -> {
            ensureActiveOrFinish(attempts.lockOwned(attemptId, studentId));
            attempts.touch(attemptId);
        });
        return studentSnapshot(attemptId, studentId);
    }

    @Override
    public Responses.Status status(UUID attemptId, UUID studentId) {
        return writes.execute(transaction -> {
            var attempt = attempts.lockOwned(attemptId, studentId);
            boolean active = ensureActiveOrFinish(attempt);
            if (active) attempts.touch(attemptId);
            return new Responses.Status(attemptId, (String) attempt.get("status"),
                    (Instant) attempt.get("deadline"), attempts.currentTime(),
                    (String) attempt.get("finish_reason"), attempts.countedViolations(attemptId));
        });
    }

    @Override
    public Responses.Id active(UUID studentId) {
        return new Responses.Id(attempts.activeId(studentId).orElse(null));
    }

    private Map<String, Object> studentSnapshot(UUID attemptId, UUID studentId) {
        // No row lock during the larger snapshot; all SELECTs see the same committed version.
        return reads.execute(transaction -> {
            var attempt = attempts.get(attemptId);
            requireOwner(attempt, studentId);
            return snapshot(attempt, false);
        });
    }

    @Override
    public PageResponse<Map<String, Object>> history(UUID studentId, PageRequest page) {
        return attempts.history(studentId, page);
    }

    @Override
    @Transactional
    public Map<String, Object> save(UUID attemptId, UUID studentId, UUID questionId, SaveAnswerRequest input) {
        var attempt = attempts.lockOwned(attemptId, studentId);
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
        var attempt = attempts.lockOwned(attemptId, studentId);
        if (ensureActiveOrFinish(attempt)) {
            boolean counted = !attempts.hasRecentCountedOccurrence(attemptId);
            attempts.addOccurrence(input.id(), attemptId, input.kind(), counted);
            applyViolationPolicy(attempt);
            liveEvents.changed(id(attempt, "session_id"));
        }
        return Map.of(
                "status", attempt.get("status"),
                "violations", attempts.countedViolations(attemptId));
    }

    @Override
    public Map<String, Object> submit(UUID attemptId, UUID studentId) {
        writes.executeWithoutResult(transaction -> {
            var attempt = attempts.lockOwned(attemptId, studentId);
            if (ensureActiveOrFinish(attempt)) finish(attempt, "ENTREGA_ALUNO");
        });
        return studentSnapshot(attemptId, studentId);
    }

    @Override
    public Map<String, Object> review(UUID attemptId, UUID teacherId) {
        writes.executeWithoutResult(transaction -> ensureActiveOrFinish(attempts.lockForTeacher(attemptId, teacherId)));
        return reads.execute(transaction -> {
            var attempt = attempts.get(attemptId);
            sessions.requireOwned(id(attempt, "session_id"), teacherId);
            return snapshot(attempt, true);
        });
    }

    @Override
    @Transactional
    public void grade(UUID attemptId, UUID teacherId, UUID questionId, GradeAnswerRequest input) {
        var attempt = attempts.lockForTeacher(attemptId, teacherId);
        UUID sessionId = id(attempt, "session_id");
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
    public void expire() {
        for (int batch = 0; batch < 10; batch++) {
            int processed = writes.execute(transaction -> {
                var expired = attempts.expiredActiveAttempts();
                for (var attempt : expired) finish(attempt, "TEMPO_ESGOTADO");
                return expired.size();
            });
            if (processed < 100) break;
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
        attempt.put("finish_reason", reason);
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
