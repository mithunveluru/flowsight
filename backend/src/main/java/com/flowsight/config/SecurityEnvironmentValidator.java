package com.flowsight.config;

import io.jsonwebtoken.io.Decoders;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

// Fails fast at startup on missing/weak security config (JWT secret, prod CORS). Never echoes the secret.
@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityEnvironmentValidator {

    private static final int MIN_JWT_SECRET_BYTES = 32; // 256 bits, required by HS256
    private static final Set<String> DEV_PROFILES = Set.of("dev", "test", "local");

    private final Environment environment;

    @Value("${application.security.jwt.secret-key:}")
    private String jwtSecret;

    @Value("${application.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @PostConstruct
    void validate() {
        validateJwtSecret();
        validateCorsOrigins();
        log.info("Security environment validation passed (profiles={})",
            Arrays.toString(environment.getActiveProfiles()));
    }

    private void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                "JWT secret is not configured. Set the JWT_SECRET environment variable to a "
                + "Base64-encoded value of at least 256 bits. Generate one with: openssl rand -base64 48");
        }
        byte[] decoded;
        try {
            decoded = Decoders.BASE64.decode(jwtSecret);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                "JWT_SECRET must be valid Base64. Generate one with: openssl rand -base64 48");
        }
        if (decoded.length < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(
                "JWT_SECRET is too weak: it decodes to " + decoded.length + " bytes, but HMAC-SHA256 "
                + "requires at least " + MIN_JWT_SECRET_BYTES + " bytes (256 bits). "
                + "Generate a stronger one with: openssl rand -base64 48");
        }
    }

    private void validateCorsOrigins() {
        if (isDevelopmentProfile()) {
            return;
        }
        if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank()) {
            throw new IllegalStateException(
                "CORS_ALLOWED_ORIGINS must be set explicitly in a production profile. "
                + "Provide a comma-separated list of allowed origins (no wildcards).");
        }
        if (corsAllowedOrigins.contains("*")) {
            throw new IllegalStateException(
                "CORS_ALLOWED_ORIGINS must not contain a wildcard '*' in a production profile. "
                + "List exact origins instead.");
        }
        if (corsAllowedOrigins.contains("localhost")) {
            log.warn("CORS_ALLOWED_ORIGINS contains 'localhost' under a non-dev profile: {}",
                corsAllowedOrigins);
        }
    }

    // no active profile counts as dev, so strict CORS doesn't block single-host runs (JWT check is unconditional)
    private boolean isDevelopmentProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return true;
        }
        return Arrays.stream(active)
            .anyMatch(p -> DEV_PROFILES.contains(p.toLowerCase()));
    }
}
