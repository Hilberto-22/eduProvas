package br.edu.avaliacoes.security;

import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.edu.avaliacoes.repository.UserRepository;

@Component
public class AdminInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;

    public AdminInitializer(UserRepository users, PasswordEncoder passwords,
                            @Value("${app.admin-email}") String email,
                            @Value("${app.admin-password}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (password.length() < 12 || password.length() > 72) {
            throw new IllegalArgumentException("ADMIN_PASSWORD precisa ter entre 12 e 72 caracteres");
        }
        users.createAdminIfMissing(UUID.randomUUID(), email.toLowerCase(Locale.ROOT), passwords.encode(password));
    }
}
