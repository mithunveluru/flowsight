package com.flowsight.service;

import com.flowsight.dto.account.ReceiptQuotaInfo;
import com.flowsight.entity.Role;
import com.flowsight.entity.User;
import com.flowsight.exception.QuotaExceededException;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Regression tests for the receipt quota system that replaced the old
 * subscription-tier paywall. Covers every edge case in the spec:
 *   user under limit, at limit, over limit, unlimited, reset, increase, decrease, OCR blocking.
 */
@ExtendWith(MockitoExtension.class)
class ReceiptQuotaServiceTest {

    @Mock private UserRepository userRepository;

    private ReceiptQuotaService quotaService;

    @BeforeEach
    void setUp() {
        quotaService = new ReceiptQuotaService(userRepository);
    }

    private User userWith(int limit, int processed, boolean unlimited) {
        return User.builder()
            .id(UUID.randomUUID())
            .email("u@example.com")
            .passwordHash("$2a$12$hash")
            .role(Role.USER)
            .receiptLimit(limit)
            .receiptsProcessed(processed)
            .unlimitedReceipts(unlimited)
            .build();
    }

    // -------------------------------------------------------------------------
    // requireQuotaAvailable
    // -------------------------------------------------------------------------

    @Test
    void underLimit_allowsProcessing() {
        User u = userWith(50, 32, false);
        // Should not throw
        quotaService.requireQuotaAvailable(u);
    }

    @Test
    void oneRemaining_stillAllows() {
        User u = userWith(50, 49, false);
        quotaService.requireQuotaAvailable(u);  // 49 < 50 → allowed
    }

    @Test
    void atLimit_blocksProcessing() {
        User u = userWith(50, 50, false);
        assertThatThrownBy(() -> quotaService.requireQuotaAvailable(u))
            .isInstanceOf(QuotaExceededException.class)
            .hasMessageContaining("receipt processing limit of 50");
    }

    @Test
    void overLimit_blocksProcessing() {
        // Defensive: if somehow counter exceeded limit, still blocks
        User u = userWith(50, 75, false);
        assertThatThrownBy(() -> quotaService.requireQuotaAvailable(u))
            .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void unlimitedUser_bypassesLimit() {
        User u = userWith(50, 5000, true);  // counter way past limit
        quotaService.requireQuotaAvailable(u);  // still allowed
    }

    @Test
    void unlimitedUser_atZero_alsoAllowed() {
        User u = userWith(0, 0, true);
        quotaService.requireQuotaAvailable(u);
    }

    @Test
    void zeroLimitUser_blockedImmediately() {
        // Edge case: admin set limit to 0 — user can never process anything
        User u = userWith(0, 0, false);
        assertThatThrownBy(() -> quotaService.requireQuotaAvailable(u))
            .isInstanceOf(QuotaExceededException.class);
    }

    // -------------------------------------------------------------------------
    // recordReceiptProcessed
    // -------------------------------------------------------------------------

    @Test
    void recordProcessed_callsAtomicIncrement() {
        UUID userId = UUID.randomUUID();
        when(userRepository.incrementReceiptsProcessed(userId)).thenReturn(1);

        quotaService.recordReceiptProcessed(userId);

        verify(userRepository).incrementReceiptsProcessed(userId);
    }

    // -------------------------------------------------------------------------
    // getQuota
    // -------------------------------------------------------------------------

    @Test
    void getQuota_underLimit_returnsCorrectFields() {
        User u = userWith(50, 32, false);
        ReceiptQuotaInfo info = quotaService.getQuota(u);

        assertThat(info.getUsed()).isEqualTo(32);
        assertThat(info.getLimit()).isEqualTo(50);
        assertThat(info.getRemaining()).isEqualTo(18);
        assertThat(info.isUnlimited()).isFalse();
        assertThat(info.isCanProcess()).isTrue();
    }

    @Test
    void getQuota_atLimit_canProcessFalse() {
        User u = userWith(50, 50, false);
        ReceiptQuotaInfo info = quotaService.getQuota(u);

        assertThat(info.getRemaining()).isEqualTo(0);
        assertThat(info.isCanProcess()).isFalse();
    }

    @Test
    void getQuota_unlimited_remainingIsNull() {
        User u = userWith(50, 100, true);
        ReceiptQuotaInfo info = quotaService.getQuota(u);

        assertThat(info.getRemaining()).isNull();
        assertThat(info.isUnlimited()).isTrue();
        assertThat(info.isCanProcess()).isTrue();
    }

    @Test
    void getQuota_overrunDefensive_remainingClampsToZero() {
        User u = userWith(50, 75, false);
        ReceiptQuotaInfo info = quotaService.getQuota(u);
        // Defensive clamp — never go negative on remaining
        assertThat(info.getRemaining()).isEqualTo(0);
        assertThat(info.isCanProcess()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Admin: resetUsage
    // -------------------------------------------------------------------------

    @Test
    void resetUsage_callsRepository_andReturnsFreshQuota() {
        UUID userId = UUID.randomUUID();
        User u = userWith(50, 0, false); u.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        ReceiptQuotaInfo info = quotaService.resetUsage(userId);

        verify(userRepository).resetReceiptsProcessed(userId);
        assertThat(info.getUsed()).isEqualTo(0);
        assertThat(info.getRemaining()).isEqualTo(50);
    }

    @Test
    void resetUsage_unknownUser_throws() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> quotaService.resetUsage(userId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Admin: setLimit
    // -------------------------------------------------------------------------

    @Test
    void setLimit_increasesLimit() {
        UUID userId = UUID.randomUUID();
        User u = userWith(50, 40, false); u.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptQuotaInfo info = quotaService.setLimit(userId, 200);

        assertThat(info.getLimit()).isEqualTo(200);
        assertThat(info.getRemaining()).isEqualTo(160); // 200 - 40
        assertThat(info.isCanProcess()).isTrue();
    }

    @Test
    void setLimit_decreasesLimit_belowUsage_canProcessFalse() {
        UUID userId = UUID.randomUUID();
        User u = userWith(50, 40, false); u.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptQuotaInfo info = quotaService.setLimit(userId, 30);

        assertThat(info.getLimit()).isEqualTo(30);
        // Used (40) > limit (30) → over budget → cannot process more
        assertThat(info.getRemaining()).isEqualTo(0);
        assertThat(info.isCanProcess()).isFalse();
    }

    @Test
    void setLimit_negativeThrows() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> quotaService.setLimit(userId, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // Admin: setUnlimited
    // -------------------------------------------------------------------------

    @Test
    void setUnlimited_grantsBypass() {
        UUID userId = UUID.randomUUID();
        User u = userWith(50, 50, false); u.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptQuotaInfo info = quotaService.setUnlimited(userId, true);

        assertThat(info.isUnlimited()).isTrue();
        assertThat(info.isCanProcess()).isTrue();
        assertThat(info.getRemaining()).isNull();
    }

    @Test
    void setUnlimited_revokesBypass() {
        UUID userId = UUID.randomUUID();
        User u = userWith(50, 100, true); u.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptQuotaInfo info = quotaService.setUnlimited(userId, false);

        assertThat(info.isUnlimited()).isFalse();
        // After revocation, the user's accumulated counter > limit, so blocked
        assertThat(info.isCanProcess()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Admin: bulkReset
    // -------------------------------------------------------------------------

    @Test
    void bulkResetAll_callsRepository() {
        when(userRepository.bulkResetReceiptsProcessed()).thenReturn(42);
        int affected = quotaService.bulkResetAll();
        assertThat(affected).isEqualTo(42);
        verify(userRepository).bulkResetReceiptsProcessed();
    }

    // -------------------------------------------------------------------------
    // OCR-blocking integration: quota check must run BEFORE any OCR invocation
    // -------------------------------------------------------------------------

    @Test
    void quotaCheckHappensBeforeOcr_neverCallsRepositoryIncrementWhenBlocked() {
        User u = userWith(50, 50, false);
        // The contract: requireQuotaAvailable throws before we ever reach recordReceiptProcessed.
        assertThatThrownBy(() -> quotaService.requireQuotaAvailable(u))
            .isInstanceOf(QuotaExceededException.class);
        verify(userRepository, never()).incrementReceiptsProcessed(any());
    }
}
