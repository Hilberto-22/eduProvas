package br.edu.avaliacoes.controller;

import br.edu.avaliacoes.api.domain.dto.request.PageRequest;
import br.edu.avaliacoes.api.domain.dto.response.PageResponse;
import br.edu.avaliacoes.api.domain.dto.response.Responses;
import org.springframework.web.bind.annotation.RequestParam;

import br.edu.avaliacoes.api.domain.dto.request.JoinExamRequest;
import br.edu.avaliacoes.api.domain.dto.request.OccurrenceRequest;
import br.edu.avaliacoes.api.domain.dto.request.SaveAnswerRequest;
import br.edu.avaliacoes.service.AttemptService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/student")
public class StudentController {
    private final AttemptService attemptService;

    public StudentController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @GetMapping("/attempts")
    public PageResponse<Responses.History> history(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return attemptService.history(userId(jwt), new PageRequest(page, size)).map(row -> Responses.from(row, Responses.History.class));
    }

    @PostMapping("/join")
    public Responses.Attempt join(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JoinExamRequest input) {
        return Responses.from(attemptService.join(userId(jwt), input.code()), Responses.Attempt.class);
    }

    @GetMapping("/attempts/{attemptId}")
    public Responses.Attempt read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return Responses.from(attemptService.read(attemptId, userId(jwt)), Responses.Attempt.class);
    }

    @GetMapping("/attempts/{attemptId}/status")
    public Responses.Status status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return attemptService.status(attemptId, userId(jwt));
    }

    @GetMapping("/active-attempt")
    public Responses.Id active(@AuthenticationPrincipal Jwt jwt) {
        return attemptService.active(userId(jwt));
    }

    @PutMapping("/attempts/{attemptId}/answers/{questionId}")
    public Responses.Saved save(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId,
                       @PathVariable UUID questionId, @Valid @RequestBody SaveAnswerRequest input) {
        return Responses.from(attemptService.save(attemptId, userId(jwt), questionId, input), Responses.Saved.class);
    }

    @PostMapping("/attempts/{attemptId}/occurrences")
    public Responses.OccurrenceResult occurrence(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId,
                             @Valid @RequestBody OccurrenceRequest input) {
        return Responses.from(attemptService.occurrence(attemptId, userId(jwt), input), Responses.OccurrenceResult.class);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public Responses.Attempt submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return Responses.from(attemptService.submit(attemptId, userId(jwt)), Responses.Attempt.class);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
