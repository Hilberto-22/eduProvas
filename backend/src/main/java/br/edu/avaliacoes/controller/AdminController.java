package br.edu.avaliacoes.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.edu.avaliacoes.api.domain.dto.request.CreateUserRequest;
import br.edu.avaliacoes.api.domain.dto.request.PageRequest;
import br.edu.avaliacoes.api.domain.dto.response.PageResponse;
import br.edu.avaliacoes.api.domain.dto.response.Responses;
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
    public PageResponse<Responses.User> users(
            @RequestParam(defaultValue = "0") int page, 
            @RequestParam(defaultValue = "10") int size) {
                
        return userService.findAll(new PageRequest(page, size))
                .map(row -> Responses.from(row, Responses.User.class));
    }

    @PostMapping("/users")
    public Responses.Id createUser(@Valid @RequestBody CreateUserRequest input) {
        return Responses.from(userService.create(input), Responses.Id.class);
    }
}
