package com.flowsight.service;

import com.flowsight.email.EmailService;
import com.flowsight.entity.PasswordResetToken;
import com.flowsight.entity.User;
import com.flowsight.exception.FlowsightException;
import com.flowsight.repository.PasswordResetTokenRepository;
import com.flowsight.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    @Value("${application.password-reset.expiration-minutes:30}")
    private long expirationMinutes;

    @Value("${application.password-reset.frontend-url:http://localhost:3007}")
    private String frontendBaseUrl;

    // DEV ONLY. When true, requestReset returns the freshly minted reset URL so
    // callers can surface it for local testing without a configured mail provider.
    // Default false; must never be enabled in a shared/production environment.
    @Value("${application.password-reset.dev-expose-link:false}")
    private boolean devExposeLink;

    /**
     * Issue a reset link for the email if it belongs to an active user.
     *
     * The caller-facing response must be identical whether or not the email
     * exists, so this returns the reset URL only as a DEV testing aid and only
     * when {@code dev-expose-link} is enabled; otherwise it returns
     * {@link Optional#empty()} regardless of whether a token was issued. This
     * keeps account enumeration impossible in normal/production operation.
     *
     * @return the freshly minted reset URL when (and only when) dev-expose-link
     *         is on and a token was issued; otherwise empty.
     */
    @Transactional
    public Optional<String> requestReset(String rawEmail) {
        String email = normalize(rawEmail);
        String requestingIp = extractIpAddress();

        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty() || !maybeUser.get().isEnabled()) {
            // Audit the attempt either way so abuse is observable, but never
            // surface this distinction to the caller.
            auditLogService.log(
                null,
                AuditLogService.ACTION_PASSWORD_RESET_REQUESTED,
                "User",
                null,
                Map.of("email", email, "outcome", "no_active_user")
            );
            return Optional.empty();
        }

        User user = maybeUser.get();

        String rawToken  = generateToken();
        String tokenHash = sha256Hex(rawToken);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(expirationMinutes));

        PasswordResetToken token = PasswordResetToken.builder()
            .user(user)
            .tokenHash(tokenHash)
            .expiresAt(expiresAt)
            .requestingIp(requestingIp)
            .build();
        tokenRepository.save(token);

        String resetUrl = buildResetUrl(rawToken);
        emailService.sendPasswordReset(user.getEmail(), displayName(user), resetUrl);

        auditLogService.log(
            user,
            AuditLogService.ACTION_PASSWORD_RESET_REQUESTED,
            "User",
            user.getId().toString(),
            Map.of("outcome", "token_issued")
        );

        log.info("Password reset token issued for user {}", user.getId());

        return devExposeLink ? Optional.of(resetUrl) : Optional.empty();
    }

    /**
     * Consume a reset token: validate, set the new password, mark the token
     * used, and invalidate every other live token for the user.
     *
     * Uses a single generic error for every failure mode (unknown token,
     * expired token, already consumed) so attackers cannot distinguish them.
     */
    @Transactional
    public void consumeReset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }

        String tokenHash = sha256Hex(rawToken);
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(this::invalidToken);

        Instant now = Instant.now();
        if (token.isConsumed() || token.isExpired(now)) {
            throw invalidToken();
        }

        User user = token.getUser();

        // Audit BEFORE the password-row write. The audit-log REQUIRES_NEW
        // transaction needs to FK-lookup users(id); doing it after we lock
        // the user row would deadlock with the outer transaction.
        auditLogService.log(
            user,
            AuditLogService.ACTION_PASSWORD_RESET_COMPLETED,
            "User",
            user.getId().toString()
        );

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setConsumedAt(now);
        tokenRepository.save(token);

        // Defense in depth: any other live token for this user is now stale.
        tokenRepository.invalidateAllForUser(user.getId(), now);

        log.info("Password reset completed for user {}", user.getId());
    }

    private static String generateToken() {
        byte[] raw = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a JDK guarantee; this branch is unreachable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String buildResetUrl(String rawToken) {
        String base = frontendBaseUrl.endsWith("/")
            ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
            : frontendBaseUrl;
        return base + "/auth/reset-password?token=" + rawToken;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.toLowerCase().trim();
    }

    private static String displayName(User user) {
        String name = user.getFullName();
        if (name == null || name.isBlank()) return user.getEmail();
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }

    private FlowsightException invalidToken() {
        return new FlowsightException(
            "This reset link is invalid or has expired. Please request a new one.",
            HttpStatus.BAD_REQUEST
        );
    }

    private String extractIpAddress() {
        if (currentRequest == null) return null;
        try {
            String forwarded = currentRequest.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
            return currentRequest.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
