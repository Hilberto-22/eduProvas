package br.edu.avaliacoes.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.edu.avaliacoes.api.domain.dto.request.CreateAssessmentRequest;
import br.edu.avaliacoes.api.domain.dto.request.CreateQuestionRequest;

public interface AssessmentService {
    List<Map<String, Object>> findByTeacher(UUID teacherId);
    Map<String, Object> create(UUID teacherId, CreateAssessmentRequest input);
    List<Map<String, Object>> findQuestions(UUID assessmentId, UUID teacherId);
    Map<String, Object> addQuestion(UUID assessmentId, UUID teacherId, CreateQuestionRequest input);
}
