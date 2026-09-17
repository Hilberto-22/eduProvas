package br.edu.avaliacoes.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.edu.avaliacoes.api.domain.dto.request.CreateExamSessionRequest;

public interface ExamSessionService {
    Map<String, Object> create(UUID teacherId, CreateExamSessionRequest input);
    void publish(UUID sessionId, UUID teacherId);
    List<Map<String, Object>> findByTeacher(UUID teacherId);
    List<Map<String, Object>> monitor(UUID sessionId, UUID teacherId);
}
