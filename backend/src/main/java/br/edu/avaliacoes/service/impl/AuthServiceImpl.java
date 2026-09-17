package br.edu.avaliacoes.service.impl;

import br.edu.avaliacoes.api.domain.dto.request.LoginRequest;
import br.edu.avaliacoes.repository.UserRepository;
import br.edu.avaliacoes.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthServiceImpl implements AuthService {
    private static final long TOKEN_DURATION_SECONDS = 28_800;

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String issuer;

    public AuthServiceImpl(UserRepository users, PasswordEncoder passwords, JwtEncoder encoder,
                           @Value("${app.jwt-issuer}") String issuer) {
        this.users = users;
        this.passwords = passwords;
        this.encoder = encoder;
        this.issuer = issuer;
    }

    @Override
    public Map<String, Object> login(LoginRequest input) {
        var user = users.findByEmail(input.email().trim().toLowerCase(Locale.ROOT))
                .filter(candidate -> passwords.matches(input.password(), (String) candidate.get("password_hash")))
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Credenciais inválidas"));

        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(((UUID) user.get("id")).toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(TOKEN_DURATION_SECONDS))
                .claim("role", user.get("role"))
                .claim("name", user.get("name"))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.get("id"),
                        "name", user.get("name"),
                        "role", user.get("role")));
    }
}
