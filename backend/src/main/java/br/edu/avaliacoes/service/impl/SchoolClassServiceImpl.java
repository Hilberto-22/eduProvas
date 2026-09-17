package br.edu.avaliacoes.service.impl;

import br.edu.avaliacoes.api.domain.dto.request.CreateClassRequest;
import br.edu.avaliacoes.api.domain.dto.request.CreateUserRequest;
import br.edu.avaliacoes.repository.SchoolClassRepository;
import br.edu.avaliacoes.repository.UserRepository;
import br.edu.avaliacoes.service.SchoolClassService;
import br.edu.avaliacoes.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class SchoolClassServiceImpl implements SchoolClassService {
    private final SchoolClassRepository classes;
    private final UserRepository users;
    private final UserService userService;

    public SchoolClassServiceImpl(SchoolClassRepository classes, UserRepository users, UserService userService) {
        this.classes = classes;
        this.users = users;
        this.userService = userService;
    }

    @Override
    public List<Map<String, Object>> findByTeacher(UUID teacherId) {
        return classes.findByTeacher(teacherId);
    }

    @Override
    public Map<String, Object> create(UUID teacherId, CreateClassRequest input) {
        UUID id = UUID.randomUUID();
        classes.create(id, input.name().trim(), teacherId);
        return Map.of("id", id);
    }

    @Override
    public List<Map<String, Object>> findStudents(UUID classId, UUID teacherId) {
        classes.requireOwned(classId, teacherId);
        return classes.findStudents(classId);
    }

    @Override
    @Transactional
    public Map<String, Object> createStudent(UUID classId, UUID teacherId, CreateUserRequest input) {
        classes.requireOwned(classId, teacherId);
        if (!"ALUNO".equals(input.role())) {
            throw new ResponseStatusException(BAD_REQUEST, "Somente alunos podem ser matriculados");
        }
        var created = userService.create(input);
        classes.enroll(classId, (UUID) created.get("id"));
        return created;
    }

    @Override
    public void enroll(UUID classId, UUID teacherId, UUID studentId) {
        classes.requireOwned(classId, teacherId);
        users.requireStudent(studentId);
        classes.enroll(classId, studentId);
    }
}
