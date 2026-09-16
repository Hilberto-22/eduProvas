package br.edu.avaliacoes.api;

import br.edu.avaliacoes.api.Inputs.*;
import br.edu.avaliacoes.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final TeacherService teacher;
    private final AttemptService attempts;

    public ApiController(TeacherService teacher, AttemptService attempts) {
        this.teacher = teacher;
        this.attempts = attempts;
    }

    private UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    @GetMapping("/admin/users")
    public Object users() {
        return teacher.users();
    }

    @PostMapping("/admin/users")
    public Object user(@Valid @RequestBody User input) {
        return teacher.user(input);
    }

    @GetMapping("/teacher/classes")
    public Object classes(@AuthenticationPrincipal Jwt jwt) {
        return teacher.classes(id(jwt));
    }

    @PostMapping("/teacher/classes")
    public Object createClass(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Named input) {
        return teacher.createClass(id(jwt), input);
    }

    @GetMapping("/teacher/classes/{classId}/students")
    public Object students(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID classId) {
        return teacher.students(classId, id(jwt));
    }

    @PostMapping("/teacher/classes/{classId}/students")
    public Object student(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID classId,
            @Valid @RequestBody User input) {
        return teacher.createStudent(classId, id(jwt), input);
    }

    @PostMapping("/teacher/classes/{classId}/enrollments")
    public void enroll(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID classId,
            @Valid @RequestBody Enrollment input) {
        teacher.enroll(classId, id(jwt), input.studentId());
    }

    @GetMapping("/teacher/assessments")
    public Object assessments(@AuthenticationPrincipal Jwt jwt) {
        return teacher.assessments(id(jwt));
    }

    @PostMapping("/teacher/assessments")
    public Object assessment(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Assessment input) {
        return teacher.createAssessment(id(jwt), input);
    }

    @GetMapping("/teacher/assessments/{assessmentId}/questions")
    public Object questions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assessmentId) {
        teacher.ownedAssessment(assessmentId, id(jwt));
        return teacher.questions(assessmentId, true);
    }

    @PostMapping("/teacher/assessments/{assessmentId}/questions")
    public Object question(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assessmentId,
            @Valid @RequestBody Question input) {
        return teacher.addQuestion(assessmentId, id(jwt), input);
    }

    @GetMapping("/teacher/sessions")
    public Object sessions(@AuthenticationPrincipal Jwt jwt) {
        return teacher.sessions(id(jwt));
    }

    @PostMapping("/teacher/sessions")
    public Object session(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Session input) {
        return teacher.createSession(id(jwt), input);
    }

    @PostMapping("/teacher/sessions/{sessionId}/publish")
    public void publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        teacher.publish(sessionId, id(jwt));
    }

    @GetMapping("/teacher/sessions/{sessionId}/monitor")
    public Object monitor(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return teacher.monitor(sessionId, id(jwt));
    }

    @GetMapping("/teacher/attempts/{attemptId}")
    public Object review(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return attempts.review(attemptId, id(jwt));
    }

    @PutMapping("/teacher/attempts/{attemptId}/grades/{questionId}")
    public void grade(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId, @PathVariable UUID questionId,
            @Valid @RequestBody Grade input) {
        attempts.grade(attemptId, id(jwt), questionId, input);
    }

    @GetMapping("/student/attempts")
    public Object history(@AuthenticationPrincipal Jwt jwt) {
        return attempts.history(id(jwt));
    }

    @PostMapping("/student/join")
    public Object join(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Join input) {
        return attempts.join(id(jwt), input.code());
    }

    @GetMapping("/student/attempts/{attemptId}")
    public Object read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return attempts.read(attemptId, id(jwt));
    }

    @PutMapping("/student/attempts/{attemptId}/answers/{questionId}")
    public Object save(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId, @PathVariable UUID questionId,
            @Valid @RequestBody Answer input) {
        return attempts.save(attemptId, id(jwt), questionId, input);
    }

    @PostMapping("/student/attempts/{attemptId}/occurrences")
    public Object occurrence(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId,
            @Valid @RequestBody Occurrence input) {
        return attempts.occurrence(attemptId, id(jwt), input);
    }

    @PostMapping("/student/attempts/{attemptId}/submit")
    public Object submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return attempts.submit(attemptId, id(jwt));
    }
}
