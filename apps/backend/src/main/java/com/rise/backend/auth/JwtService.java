package com.rise.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes:240}") long expirationMinutes
    ) {
        this.secretKey = resolveSecret(secret);
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.email())
                .claims(Map.of(
                        "userId", principal.userId(),
                        "role", principal.role().name()
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey resolveSecret(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            configuredSecret = "dev-only-insecure-secret-change-me-please-change";
        }

        byte[] candidate;
        try {
            candidate = Decoders.BASE64.decode(configuredSecret);
            if (candidate.length < 32) {
                try {
                    candidate = Decoders.BASE64URL.decode(configuredSecret);
                } catch (RuntimeException ignored) {
                    candidate = configuredSecret.getBytes(StandardCharsets.UTF_8);
                }
            }
        } catch (RuntimeException ex) {
            try {
                candidate = Decoders.BASE64URL.decode(configuredSecret);
            } catch (RuntimeException ignored) {
                candidate = configuredSecret.getBytes(StandardCharsets.UTF_8);
            }
        }

        if (candidate.length == 0) {
            candidate = "dev-only-insecure-secret-change-me-please-change".getBytes(StandardCharsets.UTF_8);
        }

        if (candidate.length < 32) {
            byte[] expanded = new byte[32];
            for (int index = 0; index < expanded.length; index++) {
                expanded[index] = candidate[index % candidate.length];
            }
            candidate = expanded;
        }

        return Keys.hmacShaKeyFor(candidate);
    }
}
