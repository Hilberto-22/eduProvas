package br.edu.avaliacoes.api.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAssessmentRequest(@NotBlank @Size(max = 200) String title) {
}
