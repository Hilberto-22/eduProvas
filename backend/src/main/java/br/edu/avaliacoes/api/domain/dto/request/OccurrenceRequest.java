package br.edu.avaliacoes.api.domain.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OccurrenceRequest(
        @NotNull UUID id,
        @NotNull @Pattern(regexp = "FOCO_PERDIDO|ABA_OCULTA|SAIDA_FULLSCREEN") String kind) {
}
