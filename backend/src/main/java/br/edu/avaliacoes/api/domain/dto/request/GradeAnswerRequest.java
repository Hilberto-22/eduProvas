package br.edu.avaliacoes.api.domain.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GradeAnswerRequest(
        @NotNull @DecimalMin("0") BigDecimal score,
        @Size(max = 5000) String feedback) {
}
