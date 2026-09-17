package br.edu.avaliacoes.controller;

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
    public Object history(@AuthenticationPrincipal Jwt jwt) {
        return attemptService.history(userId(jwt));
    }

    @PostMapping("/join")
    public Object join(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JoinExamRequest input) {
        return attemptService.join(userId(jwt), input.code());
    }

    @GetMapping("/attempts/{attemptId}")
    public Object read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return attemptService.read(attemptId, userId(jwt));
    }

    @PutMapping("/attempts/{attemptId}/answers/{questionId}")
    public Object save(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId,
                       @PathVariable UUID questionId, @Valid @RequestBody SaveAnswerRequest input) {
        return attemptService.save(attemptId, userId(jwt), questionId, input);
    }

    @PostMapping("/attempts/{attemptId}/occurrences")
    public Object occurrence(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId,
                             @Valid @RequestBody OccurrenceRequest input) {
        return attemptService.occurrence(attemptId, userId(jwt), input);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public Object submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return attemptService.submit(attemptId, userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
