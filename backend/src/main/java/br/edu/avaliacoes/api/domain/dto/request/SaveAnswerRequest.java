package br.edu.avaliacoes.api.domain.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Size;

public record SaveAnswerRequest(UUID alternativeId, @Size(max = 20000) String text) {
}
