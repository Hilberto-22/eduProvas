package br.edu.avaliacoes.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public class Inputs {
    public record User(@NotBlank @Size(max=120) String name,@Email @NotBlank @Size(max=254) String email,@Size(min=12,max=72) @NotBlank String password,@Pattern(regexp="ADMIN|PROFESSOR|ALUNO") @NotNull String role) {}
    public record Named(@NotBlank @Size(max=120) String name) {}
    public record Enrollment(@NotNull UUID studentId) {}
    public record Assessment(@NotBlank @Size(max=200) String title) {}
    public record Alternative(@NotBlank @Size(max=2000) String label, boolean correct) {}
    public record Question(@NotBlank @Size(max=10000) String prompt,@NotNull @Pattern(regexp="OBJETIVA|DISCURSIVA") String kind,@NotNull @DecimalMin("0.01") @DecimalMax("1000") BigDecimal points,@NotNull @Size(max=10) List<@Valid Alternative> alternatives) {}
    public record Session(@NotNull UUID assessmentId,@NotNull UUID classId,@NotNull Instant startsAt,@NotNull Instant endsAt,@Min(1) @Max(480) int durationMinutes,@Min(1) @Max(100) int maxViolations,@NotNull @Pattern(regexp="REGISTRAR|FINALIZAR") String violationAction) {}
    public record Join(@NotBlank @Size(max=16) String code) {}
    public record Answer(UUID alternativeId,@Size(max=20000) String text) {}
    public record Occurrence(@NotNull UUID id,@NotNull @Pattern(regexp="FOCO_PERDIDO|ABA_OCULTA|SAIDA_FULLSCREEN") String kind) {}
    public record Grade(@NotNull @DecimalMin("0") BigDecimal score,@Size(max=5000) String feedback) {}
}
