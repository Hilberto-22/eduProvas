package br.edu.avaliacoes.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;

class SecurityConfigTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void encoderAndDecoderShareAlgorithmSecretAndIssuer() {
        var key = config.key(SECRET);
        var encoder = config.encoder(key);
        var decoder = config.decoder(key, "avaliacoes");
        var token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims("avaliacoes"))).getTokenValue();

        var jwt = decoder.decode(token);
        var authentication = config.jwtConverter().convert(jwt);

        assertThat(jwt.getSubject()).isEqualTo("user-id");
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_PROFESSOR");
    }

    @Test
    void decoderRejectsTokenFromAnotherIssuer() {
        var key = config.key(SECRET);
        var token = config.encoder(key).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims("outro-sistema"))).getTokenValue();

        assertThatThrownBy(() -> config.decoder(key, "avaliacoes").decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsSecretShorterThan256Bits() {
        assertThatThrownBy(() -> config.key("segredo-curto"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    private JwtClaimsSet claims(String issuer) {
        var now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("user-id")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("role", "PROFESSOR")
                .build();
    }
}
