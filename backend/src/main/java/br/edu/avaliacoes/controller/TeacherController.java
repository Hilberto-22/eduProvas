package br.edu.avaliacoes.controller;

import br.edu.avaliacoes.api.domain.dto.request.PageRequest;
import br.edu.avaliacoes.api.domain.dto.response.PageResponse;
import br.edu.avaliacoes.api.domain.dto.response.Responses;
import org.springframework.web.bind.annotation.RequestParam;

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
    public PageResponse<Responses.SchoolClass> classes(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return classService.findByTeacher(userId(jwt), new PageRequest(page, size)).map(row -> Responses.from(row, Responses.SchoolClass.class));
    }

    @PostMapping("/classes")
    public Responses.Id createClass(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateClassRequest input) {
        return Responses.from(classService.create(userId(jwt), input), Responses.Id.class);
    }

    @GetMapping("/classes/{classId}/students")
    public PageResponse<Responses.Student> students(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID classId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return classService.findStudents(classId, userId(jwt), new PageRequest(page, size)).map(row -> Responses.from(row, Responses.Student.class));
    }

    @PostMapping("/classes/{classId}/students")
    public Responses.Id createStudent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID classId,
                                @Valid @RequestBody CreateUserRequest input) {
        return Responses.from(classService.createStudent(classId, userId(jwt), input), Responses.Id.class);
    }

    @PostMapping("/classes/{classId}/enrollments")
    public void enroll(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID classId,
                       @Valid @RequestBody EnrollmentRequest input) {
        classService.enroll(classId, userId(jwt), input.studentId());
    }

    @GetMapping("/assessments")
    public PageResponse<Responses.Assessment> assessments(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return assessmentService.findByTeacher(userId(jwt), new PageRequest(page, size)).map(row -> Responses.from(row, Responses.Assessment.class));
    }

    @PostMapping("/assessments")
    public Responses.Id createAssessment(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody CreateAssessmentRequest input) {
        return Responses.from(assessmentService.create(userId(jwt), input), Responses.Id.class);
    }

    @GetMapping("/assessments/{assessmentId}/questions")
    public java.util.List<Responses.Question> questions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assessmentId) {
        return Responses.list(assessmentService.findQuestions(assessmentId, userId(jwt)), Responses.Question.class);
    }

    @PostMapping("/assessments/{assessmentId}/questions")
    public Responses.Id addQuestion(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assessmentId,
                              @Valid @RequestBody CreateQuestionRequest input) {
        return Responses.from(assessmentService.addQuestion(assessmentId, userId(jwt), input), Responses.Id.class);
    }

    @GetMapping("/sessions")
    public PageResponse<Responses.Session> sessions(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return sessionService.findByTeacher(userId(jwt), new PageRequest(page, size)).map(row -> Responses.from(row, Responses.Session.class));
    }

    @PostMapping("/sessions")
    public Responses.CreatedSession createSession(@AuthenticationPrincipal Jwt jwt,
                                @Valid @RequestBody CreateExamSessionRequest input) {
        return Responses.from(sessionService.create(userId(jwt), input), Responses.CreatedSession.class);
    }

    @PostMapping("/sessions/{sessionId}/publish")
    public void publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        sessionService.publish(sessionId, userId(jwt));
    }

    @GetMapping("/sessions/{sessionId}/monitor")
    public PageResponse<Responses.Monitor> monitor(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return sessionService.monitor(sessionId, userId(jwt), new PageRequest(page, size)).map(row -> Responses.from(row, Responses.Monitor.class));
    }

    @GetMapping("/attempts/{attemptId}")
    public Responses.Attempt review(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return Responses.from(attemptService.review(attemptId, userId(jwt)), Responses.Attempt.class);
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
