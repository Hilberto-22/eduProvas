package br.edu.avaliacoes.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.avaliacoes.api.domain.dto.request.CreateAssessmentRequest;
import br.edu.avaliacoes.api.domain.dto.request.CreateClassRequest;
import br.edu.avaliacoes.api.domain.dto.request.CreateExamSessionRequest;
import br.edu.avaliacoes.api.domain.dto.request.CreateQuestionRequest;
import br.edu.avaliacoes.api.domain.dto.request.CreateUserRequest;
import br.edu.avaliacoes.api.domain.dto.request.EnrollmentRequest;
import br.edu.avaliacoes.api.domain.dto.request.GradeAnswerRequest;
import br.edu.avaliacoes.service.AssessmentService;
import br.edu.avaliacoes.service.AttemptService;
import br.edu.avaliacoes.service.ExamSessionService;
import br.edu.avaliacoes.service.SchoolClassService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {
    private final SchoolClassService classService;
    private final AssessmentService assessmentService;
    private final ExamSessionService sessionService;
    private final AttemptService attemptService;

    public TeacherController(SchoolClassService classService, AssessmentService assessmentService,
                             ExamSessionService sessionService, AttemptService attemptService) {
        this.classService = classService;
        this.assessmentService = assessmentService;
        this.sessionService = sessionService;
        this.attemptService = attemptService;
    }

    @GetMapping("/classes")
    public Object classes(@AuthenticationPrincipal Jwt jwt) {
        return classService.findByTeacher(userId(jwt));
    }

    @PostMapping("/classes")
    public Object createClass(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateClassRequest input) {
        return classService.create(userId(jwt), input);
    }

    @GetMapping("/classes/{classId}/students")
    public Object students(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID classId) {
        return classService.findStudents(classId, userId(jwt));
    }

    @PostMapping("/classes/{classId}/students")
    public Object createStudent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID classId,
                                @Valid @RequestBody CreateUserRequest input) {
        return classService.createStudent(classId, userId(jwt), input);
    }

    @PostMapping("/classes/{classId}/enrollments")
    public void enroll(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID classId,
                       @Valid @RequestBody EnrollmentRequest input) {
        classService.enroll(classId, userId(jwt), input.studentId());
    }

    @GetMapping("/assessments")
    public Object assessments(@AuthenticationPrincipal Jwt jwt) {
        return assessmentService.findByTeacher(userId(jwt));
    }

    @PostMapping("/assessments")
    public Object createAssessment(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody CreateAssessmentRequest input) {
        return assessmentService.create(userId(jwt), input);
    }

    @GetMapping("/assessments/{assessmentId}/questions")
    public Object questions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assessmentId) {
        return assessmentService.findQuestions(assessmentId, userId(jwt));
    }

    @PostMapping("/assessments/{assessmentId}/questions")
    public Object addQuestion(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assessmentId,
                              @Valid @RequestBody CreateQuestionRequest input) {
        return assessmentService.addQuestion(assessmentId, userId(jwt), input);
    }

    @GetMapping("/sessions")
    public Object sessions(@AuthenticationPrincipal Jwt jwt) {
        return sessionService.findByTeacher(userId(jwt));
    }

    @PostMapping("/sessions")
    public Object createSession(@AuthenticationPrincipal Jwt jwt,
                                @Valid @RequestBody CreateExamSessionRequest input) {
        return sessionService.create(userId(jwt), input);
    }

    @PostMapping("/sessions/{sessionId}/publish")
    public void publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        sessionService.publish(sessionId, userId(jwt));
    }

    @GetMapping("/sessions/{sessionId}/monitor")
    public Object monitor(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return sessionService.monitor(sessionId, userId(jwt));
    }

    @GetMapping("/attempts/{attemptId}")
    public Object review(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return attemptService.review(attemptId, userId(jwt));
    }

    @PutMapping("/attempts/{attemptId}/grades/{questionId}")
    public void grade(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId,
                      @PathVariable UUID questionId, @Valid @RequestBody GradeAnswerRequest input) {
        attemptService.grade(attemptId, userId(jwt), questionId, input);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
