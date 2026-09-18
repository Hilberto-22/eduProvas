package br.edu.avaliacoes.service;

import java.util.Map;

import br.edu.avaliacoes.api.domain.dto.request.CreateUserRequest;
import br.edu.avaliacoes.api.domain.dto.request.PageRequest;
import br.edu.avaliacoes.api.domain.dto.response.PageResponse;

public interface UserService {
    PageResponse<Map<String, Object>> findAll(PageRequest page);
    Map<String, Object> create(CreateUserRequest input);
}
