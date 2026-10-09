package com.ticketforge.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo-only token issuer so the curl walkthrough works without an identity provider. */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "ticketforge.security.dev-token-endpoint-enabled", havingValue = "true")
public class AuthController {
    private final JwtService jwtService;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AuthController(JwtService jwtService, JdbcTemplate jdbc, Clock clock) {
        this.jwtService = jwtService;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record TokenRequest(@NotBlank @Size(max = 64) String userId, Role role) {
    }

    public record TokenResponse(String token, Instant expiresAt) {
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        Role role = request.role() == null ? Role.USER : request.role();
        jdbc.update("INSERT INTO users (id, display_name, role) VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING",
                request.userId(), request.userId(), role.name());
        String token = jwtService.issue(request.userId(), role);
        return new TokenResponse(token, jwtService.expiryFrom(clock.instant()));
    }
}
