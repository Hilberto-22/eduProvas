package br.edu.avaliacoes.service.impl;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.edu.avaliacoes.api.domain.dto.request.CreateUserRequest;
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
    public List<Map<String, Object>> findAll() {
        return users.findAll();
    }

    @Override
    public Map<String, Object> create(CreateUserRequest input) {
        UUID id = UUID.randomUUID();
        users.create(id, input.name().trim(), input.email().trim().toLowerCase(Locale.ROOT),
                passwords.encode(input.password()), input.role());
        return Map.of("id", id);
    }
}
