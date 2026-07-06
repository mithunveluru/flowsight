package com.flowsight.service;

import com.flowsight.entity.RefreshToken;
import com.flowsight.entity.User;
import com.flowsight.exception.FlowsightException;
import com.flowsight.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

// Rotating opaque refresh tokens (OWASP session-management pattern).
// Only SHA-256 hashes touch the database. Every refresh consumes the presented
// token and issues a successor; presenting an already-consumed or revoked token
// is treated as theft and revokes every live token for that user.
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokenRepository;

    @Value("${application.security.jwt.refresh-expiration:1209600000}") // 14 days
    private long refreshExpirationMillis;

    public record Rotation(User user, String rawToken) {}

    @Transactional
    public String issue(User user) {
        String rawToken = generateToken();
        tokenRepository.save(RefreshToken.builder()
            .user(user)
            .tokenHash(sha256Hex(rawToken))
            .expiresAt(Instant.now().plus(Duration.ofMillis(refreshExpirationMillis)))
            .build());
        return rawToken;
    }

    // noRollbackFor: the theft response (revoke-all) must COMMIT even though the
    // request itself is rejected with an exception afterwards
    @Transactional(noRollbackFor = FlowsightException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken token = tokenRepository.findByTokenHash(hashOrThrow(rawToken))
            .orElseThrow(this::invalidToken);

        Instant now = Instant.now();
        if (token.isConsumed() || token.isRevoked()) {
            // a rotated-away token came back: assume theft, kill the user's sessions
            int revoked = tokenRepository.revokeAllForUser(token.getUser().getId(), now);
            log.warn("Refresh token reuse detected for user {}; revoked {} live tokens",
                token.getUser().getId(), revoked);
            throw invalidToken();
        }
        if (token.isExpired(now) || !token.getUser().isEnabled()) {
            throw invalidToken();
        }

        token.setConsumedAt(now);
        tokenRepository.save(token);
        return new Rotation(token.getUser(), issue(token.getUser()));
    }

    // Logout. Idempotent and silent on unknown tokens: no validity oracle.
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        Optional<RefreshToken> token = tokenRepository.findByTokenHash(sha256Hex(rawToken));
        token.ifPresent(t -> {
            if (!t.isRevoked()) {
                t.setRevokedAt(Instant.now());
                tokenRepository.save(t);
            }
        });
    }

    // Password change / admin action: invalidate every live session.
    @Transactional
    public void revokeAllForUser(java.util.UUID userId) {
        tokenRepository.revokeAllForUser(userId, Instant.now());
    }

    // Housekeeping: purge rows dead for more than 30 days.
    @Scheduled(fixedDelayString = "PT6H")
    @Transactional
    public void purgeExpired() {
        int deleted = tokenRepository.deleteOlderThan(Instant.now().minus(Duration.ofDays(30)));
        if (deleted > 0) log.debug("Purged {} dead refresh tokens", deleted);
    }

    private String hashOrThrow(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw invalidToken();
        return sha256Hex(rawToken);
    }

    private FlowsightException invalidToken() {
        return new FlowsightException("Session expired. Please sign in again.", HttpStatus.UNAUTHORIZED);
    }

    private static String generateToken() {
        byte[] raw = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
