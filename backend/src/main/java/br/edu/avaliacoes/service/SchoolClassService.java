package br.edu.avaliacoes.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.edu.avaliacoes.api.domain.dto.request.CreateClassRequest;
import br.edu.avaliacoes.api.domain.dto.request.CreateUserRequest;

public interface SchoolClassService {
    List<Map<String, Object>> findByTeacher(UUID teacherId);
    Map<String, Object> create(UUID teacherId, CreateClassRequest input);
    List<Map<String, Object>> findStudents(UUID classId, UUID teacherId);
    Map<String, Object> createStudent(UUID classId, UUID teacherId, CreateUserRequest input);
    void enroll(UUID classId, UUID teacherId, UUID studentId);
}
