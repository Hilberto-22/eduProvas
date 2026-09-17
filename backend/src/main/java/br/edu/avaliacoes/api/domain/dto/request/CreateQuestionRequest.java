package br.edu.avaliacoes.api.domain.dto.request;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateQuestionRequest(
        @NotBlank @Size(max = 10000) String prompt,
        @NotNull @Pattern(regexp = "OBJETIVA|DISCURSIVA") String kind,
        @NotNull @DecimalMin("0.01") @DecimalMax("1000") BigDecimal points,
        @NotNull @Size(max = 5) List<@Valid AlternativeRequest> alternatives) {
}
