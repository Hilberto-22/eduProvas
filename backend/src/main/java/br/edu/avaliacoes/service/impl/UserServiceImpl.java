package br.edu.avaliacoes.service.impl;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.edu.avaliacoes.api.domain.dto.request.CreateUserRequest;
import br.edu.avaliacoes.api.domain.dto.request.PageRequest;
import br.edu.avaliacoes.api.domain.dto.response.PageResponse;
import br.edu.avaliacoes.repository.UserRepository;
import br.edu.avaliacoes.service.UserService;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public UserServiceImpl(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Override
    public PageResponse<Map<String, Object>> findAll(PageRequest page) {
        var usuarios = users.findAll(page);
        return usuarios;
    }

    @Override
    public Map<String, Object> create(CreateUserRequest input) {
        UUID id = UUID.randomUUID();
        users.create(id, input.name().trim(), input.email().trim().toLowerCase(Locale.ROOT),
                passwords.encode(input.password()), input.role());
        return Map.of("id", id);
    }
}
