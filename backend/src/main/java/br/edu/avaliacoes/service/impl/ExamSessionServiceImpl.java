package br.edu.avaliacoes.service.impl;

import br.edu.avaliacoes.api.domain.dto.request.CreateExamSessionRequest;
import br.edu.avaliacoes.repository.AssessmentRepository;
import br.edu.avaliacoes.repository.ExamSessionRepository;
import br.edu.avaliacoes.repository.SchoolClassRepository;
import br.edu.avaliacoes.service.ExamSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ExamSessionServiceImpl implements ExamSessionService {
    private final AssessmentRepository assessments;
    private final SchoolClassRepository classes;
    private final ExamSessionRepository sessions;

    public ExamSessionServiceImpl(AssessmentRepository assessments, SchoolClassRepository classes,
                                  ExamSessionRepository sessions) {
        this.assessments = assessments;
        this.classes = classes;
        this.sessions = sessions;
    }

    @Override
    @Transactional
    public Map<String, Object> create(UUID teacherId, CreateExamSessionRequest input) {
        assessments.requireOwnedForUpdate(input.assessmentId(), teacherId);
        classes.requireOwned(input.classId(), teacherId);
        if (!input.endsAt().isAfter(input.startsAt())) {
            throw new ResponseStatusException(BAD_REQUEST, "Fim deve ser posterior ao início");
        }
        if (!assessments.hasQuestions(input.assessmentId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Adicione questões primeiro");
        }

        UUID id = UUID.randomUUID();
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        sessions.create(id, code, input);
        return Map.of("id", id, "code", code);
    }

    @Override
    public void publish(UUID sessionId, UUID teacherId) {
        sessions.requireOwned(sessionId, teacherId);
        sessions.publish(sessionId);
    }

    @Override
    public List<Map<String, Object>> findByTeacher(UUID teacherId) {
        return sessions.findByTeacher(teacherId);
    }

    @Override
    public List<Map<String, Object>> monitor(UUID sessionId, UUID teacherId) {
        sessions.requireOwned(sessionId, teacherId);
        return sessions.monitor(sessionId);
    }
}
