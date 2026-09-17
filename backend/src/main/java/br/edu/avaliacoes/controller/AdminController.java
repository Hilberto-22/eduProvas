package br.edu.avaliacoes.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.avaliacoes.api.domain.dto.request.CreateUserRequest;
import br.edu.avaliacoes.service.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public Object users() {
        return userService.findAll();
    }

    @PostMapping("/users")
    public Object createUser(@Valid @RequestBody CreateUserRequest input) {
        return userService.create(input);
    }
}
