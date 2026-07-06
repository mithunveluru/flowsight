package com.flowsight.service;

import com.flowsight.entity.RefreshToken;
import com.flowsight.entity.Role;
import com.flowsight.entity.User;
import com.flowsight.exception.FlowsightException;
import com.flowsight.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Rotation invariants: a live token rotates into a successor exactly once;
// presenting a consumed/revoked token is theft and revokes the whole session set.
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository tokenRepository;

    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(tokenRepository);
        ReflectionTestUtils.setField(service, "refreshExpirationMillis",
            Duration.ofDays(14).toMillis());
        user = User.builder()
            .email("user@example.com")
            .passwordHash("x")
            .fullName("Test User")
            .role(Role.USER)
            .active(true)
            .build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    @Test
    void issueStoresOnlyTheHash() {
        String raw = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
            .isNotEqualTo(raw)
            .hasSize(64); // SHA-256 hex
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void rotateConsumesAndIssuesSuccessor() {
        RefreshToken live = RefreshToken.builder()
            .user(user)
            .tokenHash("h")
            .expiresAt(Instant.now().plus(Duration.ofDays(1)))
            .build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(live));

        RefreshTokenService.Rotation rotation = service.rotate("some-raw-token");

        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.rawToken()).isNotBlank();
        assertThat(live.isConsumed()).isTrue();
    }

    @Test
    void reusingAConsumedTokenRevokesAllUserSessions() {
        RefreshToken consumed = RefreshToken.builder()
            .user(user)
            .tokenHash("h")
            .expiresAt(Instant.now().plus(Duration.ofDays(1)))
            .consumedAt(Instant.now().minusSeconds(60))
            .build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(consumed));

        assertThatThrownBy(() -> service.rotate("stolen-token"))
            .isInstanceOf(FlowsightException.class);
        verify(tokenRepository).revokeAllForUser(eq(user.getId()), any());
    }

    @Test
    void expiredTokenIsRejected() {
        RefreshToken expired = RefreshToken.builder()
            .user(user)
            .tokenHash("h")
            .expiresAt(Instant.now().minusSeconds(1))
            .build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("old-token"))
            .isInstanceOf(FlowsightException.class);
    }
}
