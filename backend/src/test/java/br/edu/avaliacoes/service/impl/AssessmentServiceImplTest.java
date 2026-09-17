package br.edu.avaliacoes.service.impl;

import br.edu.avaliacoes.api.domain.dto.request.AlternativeRequest;
import br.edu.avaliacoes.api.domain.dto.request.CreateQuestionRequest;
import br.edu.avaliacoes.repository.AssessmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceImplTest {
    @Mock AssessmentRepository assessments;
    @InjectMocks AssessmentServiceImpl service;

    private final UUID assessmentId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();

    @Test
    void acceptsObjectiveQuestionWithFiveAlternatives() {
        when(assessments.nextQuestionPosition(assessmentId)).thenReturn(1);
        var alternatives = List.of(
                new AlternativeRequest("A", true),
                new AlternativeRequest("B", false),
                new AlternativeRequest("C", false),
                new AlternativeRequest("D", false),
                new AlternativeRequest("E", false));

        service.addQuestion(assessmentId, teacherId,
                new CreateQuestionRequest("Enunciado", "OBJETIVA", BigDecimal.ONE, alternatives));

        verify(assessments, times(5)).createAlternative(any(), any(), anyString(), anyBoolean(), anyInt());
    }

    @Test
    void rejectsObjectiveQuestionWithMoreThanFiveAlternatives() {
        var alternatives = List.of(
                new AlternativeRequest("A", true),
                new AlternativeRequest("B", false),
                new AlternativeRequest("C", false),
                new AlternativeRequest("D", false),
                new AlternativeRequest("E", false),
                new AlternativeRequest("F", false));

        assertThatThrownBy(() -> service.addQuestion(assessmentId, teacherId,
                new CreateQuestionRequest("Enunciado", "OBJETIVA", BigDecimal.ONE, alternatives)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("duas a cinco alternativas");

        verify(assessments, never()).createQuestion(any(), any(), anyString(), anyString(), any(), anyInt());
    }
}
