package br.edu.avaliacoes.service;

import java.util.List;
import java.util.Map;

import br.edu.avaliacoes.api.domain.dto.request.CreateUserRequest;

public interface UserService {
    List<Map<String, Object>> findAll();
    Map<String, Object> create(CreateUserRequest input);
}
