package br.edu.avaliacoes.api.domain.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record EnrollmentRequest(@NotNull UUID studentId) {
}
