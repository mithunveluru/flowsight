package com.flowsight.service;

import com.flowsight.email.EmailService;
import com.flowsight.entity.PasswordResetToken;
import com.flowsight.entity.Role;
import com.flowsight.entity.User;
import com.flowsight.exception.FlowsightException;
import com.flowsight.repository.PasswordResetTokenRepository;
import com.flowsight.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
            userRepository,
            tokenRepository,
            passwordEncoder,
            emailService,
            auditLogService
        );
        ReflectionTestUtils.setField(service, "expirationMinutes", 30L);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://app.example");
    }

    @Test
    void requestReset_unknownEmail_audits_butDoesNotIssueOrSend() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.requestReset("ghost@example.com");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
        verify(auditLogService).log(
            eq(null),
            eq(AuditLogService.ACTION_PASSWORD_RESET_REQUESTED),
            eq("User"),
            eq(null),
            any()
        );
    }

    @Test
    void requestReset_inactiveUser_audits_butDoesNotIssue() {
        User inactive = activeUser();
        inactive.setActive(false);
        when(userRepository.findByEmail("disabled@example.com")).thenReturn(Optional.of(inactive));

        service.requestReset("disabled@example.com");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    @Test
    void requestReset_activeUser_issuesHashedToken_andDispatchesEmail() {
        User user = activeUser();
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestReset("ada@example.com");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();

        assertThat(saved.getTokenHash()).hasSize(64);
        assertThat(saved.getTokenHash()).matches("[0-9a-f]{64}");
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getConsumedAt()).isNull();
        assertThat(saved.getExpiresAt())
            .isAfter(Instant.now().plus(Duration.ofMinutes(29)))
            .isBefore(Instant.now().plus(Duration.ofMinutes(31)));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(eq("ada@example.com"), eq("Ada"), urlCaptor.capture());
        assertThat(urlCaptor.getValue())
            .startsWith("https://app.example/auth/reset-password?token=")
            // The raw token in the URL must hash to what we stored. This is the
            // critical invariant: storage is by hash, transit is the raw token.
            .satisfies(url -> {
                String raw = url.substring(url.indexOf("token=") + "token=".length());
                assertThat(sha256Hex(raw)).isEqualTo(saved.getTokenHash());
            });
    }

    @Test
    void requestReset_normalizesEmail() {
        User user = activeUser();
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestReset("  Ada@Example.com  ");

        verify(userRepository).findByEmail("ada@example.com");
    }

    @Test
    void requestReset_auditMetadataNeverContainsTheRawToken() {
        User user = activeUser();
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestReset("ada@example.com");

        // The raw token leaves the service only via the email URL. Capture both
        // the audit metadata and the dispatched URL, extract the raw token
        // from the URL, and assert the audit metadata contains no value resembling it.
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(anyString(), anyString(), urlCaptor.capture());
        String raw = urlCaptor.getValue().substring(urlCaptor.getValue().indexOf("token=") + "token=".length());

        ArgumentCaptor<java.util.Map<String, Object>> metaCaptor =
            ArgumentCaptor.forClass(java.util.Map.class);
        verify(auditLogService).log(
            eq(user),
            eq(AuditLogService.ACTION_PASSWORD_RESET_REQUESTED),
            eq("User"),
            eq(user.getId().toString()),
            metaCaptor.capture()
        );
        assertThat(metaCaptor.getValue()).containsEntry("outcome", "token_issued");
        assertThat(metaCaptor.getValue().values())
            .as("audit metadata must not include the raw token")
            .noneMatch(v -> v != null && v.toString().contains(raw));
    }

    @Test
    void consumeReset_validToken_setsNewPassword_marksConsumed_invalidatesOthers() {
        User user = activeUser();
        String raw = "T0kenRawForTest";
        String hash = sha256Hex(raw);
        PasswordResetToken token = liveTokenFor(user, hash);

        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPass123")).thenReturn("encoded-new-password");

        service.consumeReset(raw, "NewPass123");

        assertThat(user.getPasswordHash()).isEqualTo("encoded-new-password");
        assertThat(token.getConsumedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
        verify(tokenRepository).invalidateAllForUser(eq(user.getId()), any(Instant.class));
        verify(auditLogService).log(
            eq(user),
            eq(AuditLogService.ACTION_PASSWORD_RESET_COMPLETED),
            eq("User"),
            eq(user.getId().toString())
        );
    }

    @Test
    void consumeReset_unknownToken_throwsGenericError_doesNotTouchPassword() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeReset("any-token", "NewPass123"))
            .isInstanceOf(FlowsightException.class)
            .hasMessageContaining("invalid or has expired");

        verify(userRepository, never()).save(any());
    }

    @Test
    void consumeReset_expiredToken_rejected() {
        User user = activeUser();
        String raw = "RawT0ken";
        String hash = sha256Hex(raw);
        PasswordResetToken expired = PasswordResetToken.builder()
            .user(user)
            .tokenHash(hash)
            .expiresAt(Instant.now().minus(Duration.ofMinutes(1)))
            .build();
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.consumeReset(raw, "NewPass123"))
            .isInstanceOf(FlowsightException.class);

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).invalidateAllForUser(any(), any());
    }

    @Test
    void consumeReset_alreadyConsumedToken_rejected_evenIfWithinExpiry() {
        User user = activeUser();
        String raw = "Raw2";
        String hash = sha256Hex(raw);
        PasswordResetToken consumed = liveTokenFor(user, hash);
        consumed.setConsumedAt(Instant.now().minus(Duration.ofMinutes(1)));
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(consumed));

        assertThatThrownBy(() -> service.consumeReset(raw, "NewPass123"))
            .isInstanceOf(FlowsightException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void consumeReset_blankToken_rejectedWithoutLookup() {
        assertThatThrownBy(() -> service.consumeReset("", "NewPass123"))
            .isInstanceOf(FlowsightException.class);
        assertThatThrownBy(() -> service.consumeReset(null, "NewPass123"))
            .isInstanceOf(FlowsightException.class);

        verify(tokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void issuedTokens_areUnique_acrossManyInvocations() {
        User user = activeUser();
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        lenient().when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 50; i++) {
            service.requestReset("ada@example.com");
        }
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository, times(50)).save(captor.capture());
        captor.getAllValues().forEach(t -> seen.add(t.getTokenHash()));
        assertThat(seen).hasSize(50);
    }

    private User activeUser() {
        return User.builder()
            .id(UUID.randomUUID())
            .email("ada@example.com")
            .passwordHash("old-encoded-password")
            .fullName("Ada Lovelace")
            .role(Role.USER)
            .active(true)
            .build();
    }

    private PasswordResetToken liveTokenFor(User user, String hash) {
        return PasswordResetToken.builder()
            .user(user)
            .tokenHash(hash)
            .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
            .build();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
