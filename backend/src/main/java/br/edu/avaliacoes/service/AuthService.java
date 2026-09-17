package br.edu.avaliacoes.service;

import java.util.Map;

import br.edu.avaliacoes.api.domain.dto.request.LoginRequest;

public interface AuthService {
    Map<String, Object> login(LoginRequest input);
}
