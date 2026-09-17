package br.edu.avaliacoes.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.edu.avaliacoes.api.domain.dto.request.GradeAnswerRequest;
import br.edu.avaliacoes.api.domain.dto.request.OccurrenceRequest;
import br.edu.avaliacoes.api.domain.dto.request.SaveAnswerRequest;

public interface AttemptService {

    Map<String, Object> join(UUID studentId, String code);

    Map<String, Object> read(UUID attemptId, UUID studentId);

    List<Map<String, Object>> history(UUID studentId);

    Map<String, Object> save(UUID attemptId, UUID studentId, UUID questionId, SaveAnswerRequest input);

    Map<String, Object> occurrence(UUID attemptId, UUID studentId, OccurrenceRequest input);

    Map<String, Object> submit(UUID attemptId, UUID studentId);

    Map<String, Object> review(UUID attemptId, UUID teacherId);

    void grade(UUID attemptId, UUID teacherId, UUID questionId, GradeAnswerRequest input);

    void expire();
}
