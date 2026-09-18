package br.edu.avaliacoes.controller;

import br.edu.avaliacoes.api.domain.dto.request.PageRequest;
import br.edu.avaliacoes.api.domain.dto.response.PageResponse;
import br.edu.avaliacoes.api.domain.dto.response.Responses;
import org.springframework.web.bind.annotation.RequestParam;

import br.edu.avaliacoes.api.domain.dto.request.LoginRequest;
import br.edu.avaliacoes.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Responses.Login login(@Valid @RequestBody LoginRequest input) {
        return Responses.from(authService.login(input), Responses.Login.class);
    }
}
