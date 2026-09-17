package br.edu.avaliacoes.api.domain.dto.request;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateExamSessionRequest(
        @NotNull UUID assessmentId,
        @NotNull UUID classId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Min(1) @Max(480) int durationMinutes,
        @Min(1) @Max(100) int maxViolations,
        @NotNull @Pattern(regexp = "REGISTRAR|FINALIZAR") String violationAction) {
}
