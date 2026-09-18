package br.edu.avaliacoes.service;

import br.edu.avaliacoes.api.domain.dto.request.PageRequest;
import br.edu.avaliacoes.api.domain.dto.response.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.edu.avaliacoes.api.domain.dto.request.CreateAssessmentRequest;
import br.edu.avaliacoes.api.domain.dto.request.CreateQuestionRequest;

public interface AssessmentService {
    PageResponse<Map<String, Object>> findByTeacher(UUID teacherId, PageRequest page);
    Map<String, Object> create(UUID teacherId, CreateAssessmentRequest input);
    List<Map<String, Object>> findQuestions(UUID assessmentId, UUID teacherId);
    Map<String, Object> addQuestion(UUID assessmentId, UUID teacherId, CreateQuestionRequest input);
}
