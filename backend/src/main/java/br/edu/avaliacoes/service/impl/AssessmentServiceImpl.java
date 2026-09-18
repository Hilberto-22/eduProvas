package br.edu.avaliacoes.service.impl;

import br.edu.avaliacoes.api.domain.dto.request.AlternativeRequest;
import br.edu.avaliacoes.api.domain.dto.request.CreateAssessmentRequest;
import br.edu.avaliacoes.api.domain.dto.request.CreateQuestionRequest;
import br.edu.avaliacoes.repository.AssessmentRepository;
import br.edu.avaliacoes.service.AssessmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.avaliacoes.api.domain.dto.request.PageRequest;
import br.edu.avaliacoes.api.domain.dto.response.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class AssessmentServiceImpl implements AssessmentService {
    private static final int MINIMUM_OBJECTIVE_ALTERNATIVES = 2;
    private static final int MAXIMUM_OBJECTIVE_ALTERNATIVES = 5;

    private final AssessmentRepository assessments;

    public AssessmentServiceImpl(AssessmentRepository assessments) {
        this.assessments = assessments;
    }

    @Override
    public PageResponse<Map<String, Object>> findByTeacher(UUID teacherId, PageRequest page) {
        return assessments.findByTeacher(teacherId, page);
    }

    @Override
    public Map<String, Object> create(UUID teacherId, CreateAssessmentRequest input) {
        UUID id = UUID.randomUUID();
        assessments.create(id, input.title().trim(), teacherId);
        return Map.of("id", id);
    }

    @Override
    public List<Map<String, Object>> findQuestions(UUID assessmentId, UUID teacherId) {
        assessments.requireOwned(assessmentId, teacherId);
        return assessments.findQuestions(assessmentId, true);
    }

    @Override
    @Transactional
    public Map<String, Object> addQuestion(UUID assessmentId, UUID teacherId, CreateQuestionRequest input) {
        assessments.requireOwnedForUpdate(assessmentId, teacherId);
        if (assessments.hasSessions(assessmentId)) {
            throw new ResponseStatusException(CONFLICT, "Avaliação congelada: já possui aplicação");
        }
        validateQuestion(input);

        UUID questionId = UUID.randomUUID();
        assessments.createQuestion(questionId, assessmentId, input.prompt().trim(), input.kind(),
                input.points(), assessments.nextQuestionPosition(assessmentId));

        int position = 1;
        for (var alternative : input.alternatives()) {
            assessments.createAlternative(UUID.randomUUID(), questionId, alternative.label().trim(),
                    alternative.correct(), position++);
        }
        return Map.of("id", questionId);
    }

    private void validateQuestion(CreateQuestionRequest input) {
        if ("DISCURSIVA".equals(input.kind())) {
            if (!input.alternatives().isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "Questão discursiva não possui alternativas");
            }
            return;
        }

        int alternativeCount = input.alternatives().size();
        if (alternativeCount < MINIMUM_OBJECTIVE_ALTERNATIVES ||
                alternativeCount > MAXIMUM_OBJECTIVE_ALTERNATIVES) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Questão objetiva deve possuir de duas a cinco alternativas");
        }
        if (input.alternatives().stream().filter(AlternativeRequest::correct).count() != 1) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Questão objetiva deve possuir um único gabarito");
        }
    }
}
