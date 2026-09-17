package br.edu.avaliacoes.api.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClassRequest(@NotBlank @Size(max = 120) String name) {
}
