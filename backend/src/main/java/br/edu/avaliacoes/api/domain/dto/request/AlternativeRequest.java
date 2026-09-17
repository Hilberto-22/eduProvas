package br.edu.avaliacoes.api.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlternativeRequest(@NotBlank @Size(max = 2000) String label, boolean correct) {
}
