package br.edu.avaliacoes.service;

import br.edu.avaliacoes.api.domain.dto.request.PageRequest;
import br.edu.avaliacoes.api.domain.dto.response.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.edu.avaliacoes.api.domain.dto.request.CreateClassRequest;
import br.edu.avaliacoes.api.domain.dto.request.CreateUserRequest;

public interface SchoolClassService {
    PageResponse<Map<String, Object>> findByTeacher(UUID teacherId, PageRequest page);
    Map<String, Object> create(UUID teacherId, CreateClassRequest input);
    PageResponse<Map<String, Object>> findStudents(UUID classId, UUID teacherId, PageRequest page);
    Map<String, Object> createStudent(UUID classId, UUID teacherId, CreateUserRequest input);
    void enroll(UUID classId, UUID teacherId, UUID studentId);
}
