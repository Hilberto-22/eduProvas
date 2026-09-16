package br.edu.avaliacoes.security;

import java.time.Instant;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate db; private final PasswordEncoder passwords; private final JwtEncoder encoder;
    public AuthController(JdbcTemplate db, PasswordEncoder passwords, JwtEncoder encoder) { this.db=db; this.passwords=passwords; this.encoder=encoder; }
    public record Login(@NotBlank @Email String email, @NotBlank @Size(max=72) String password) {}
    @PostMapping("/login") public Map<String,Object> login(@Valid @RequestBody Login input) {
        var users=db.queryForList("SELECT * FROM app_user WHERE email=?", input.email().trim().toLowerCase(Locale.ROOT));
        if(users.isEmpty() || !passwords.matches(input.password(), (String)users.getFirst().get("password_hash"))) throw new ResponseStatusException(UNAUTHORIZED,"Credenciais inválidas");
        var u=users.getFirst(); var now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer("avaliacoes").subject(u.get("id").toString()).issuedAt(now).expiresAt(now.plusSeconds(28800)).claim("role",u.get("role")).claim("name",u.get("name")).build();
        var token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        return Map.of("token",token,"user",Map.of("id",u.get("id"),"name",u.get("name"),"role",u.get("role")));
    }
    @Bean CommandLineRunner bootstrap(@Value("${app.admin-email}") String email,@Value("${app.admin-password}") String password) {
        return args -> {
            if(password.length()<12 || password.length()>72) throw new IllegalArgumentException("ADMIN_PASSWORD precisa ter entre 12 e 72 caracteres");
            db.update("INSERT INTO app_user(id,name,email,password_hash,role) VALUES(?,?,?,?, 'ADMIN') ON CONFLICT(email) DO NOTHING",UUID.randomUUID(),"Administrador",email.toLowerCase(Locale.ROOT),passwords.encode(password));
        };
    }
}
