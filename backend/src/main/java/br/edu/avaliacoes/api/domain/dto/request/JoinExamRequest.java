package br.edu.avaliacoes.api.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinExamRequest(@NotBlank @Size(max = 16) String code) {
}
