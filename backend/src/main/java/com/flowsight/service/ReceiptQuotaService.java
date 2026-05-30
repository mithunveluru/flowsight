package com.flowsight.service;

import com.flowsight.dto.account.ReceiptQuotaInfo;
import com.flowsight.entity.User;
import com.flowsight.exception.QuotaExceededException;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Central authority for the receipt OCR quota. Every receipt-processing path
 * goes through this service so the limit logic is never duplicated.
 *
 * <p>Quota model:
 * <ul>
 *   <li>Each user has a {@code receiptLimit} (default 50, configurable per user)</li>
 *   <li>Each successful OCR invocation increments {@code receiptsProcessed} atomically</li>
 *   <li>Admins can flip {@code unlimitedReceipts} to bypass the cap entirely</li>
 * </ul>
 *
 * <p>The check is intentionally cheap (a single DB row read). The increment is an
 * atomic JPQL UPDATE so concurrent uploads cannot overshoot the cap by more than
 * one request — and the {@link #requireQuotaAvailable} check is performed
 * <em>before</em> any OCR API call, never after, so we never consume external
 * resources on a quota-blocked request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptQuotaService {

    private final UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Quota checks
    // -------------------------------------------------------------------------

    /**
     * Throws {@link QuotaExceededException} when the user has no remaining quota.
     * Call this <em>before</em> invoking any OCR resource.
     */
    public void requireQuotaAvailable(User user) {
        if (user.isUnlimitedReceipts()) return;
        if (user.getReceiptsProcessed() < user.getReceiptLimit()) return;

        throw new QuotaExceededException(String.format(
            "You've reached your receipt processing limit of %d. " +
            "Your existing receipts and analytics remain fully accessible — only new receipt OCR is blocked.",
            user.getReceiptLimit()
        ));
    }

    /**
     * Atomic increment of the user's processed-receipts counter.
     * Call only <em>after</em> the OCR has actually been invoked (success or failure).
     */
    @Transactional
    public void recordReceiptProcessed(UUID userId) {
        int updated = userRepository.incrementReceiptsProcessed(userId);
        if (updated != 1) {
            log.warn("Receipt counter increment touched {} rows for user {}", updated, userId);
        }
    }

    // -------------------------------------------------------------------------
    // Read API (for UI)
    // -------------------------------------------------------------------------

    public ReceiptQuotaInfo getQuota(User user) {
        boolean unlimited = user.isUnlimitedReceipts();
        Integer remaining = unlimited ? null
            : Math.max(0, user.getReceiptLimit() - user.getReceiptsProcessed());

        return ReceiptQuotaInfo.builder()
            .used(user.getReceiptsProcessed())
            .limit(user.getReceiptLimit())
            .remaining(remaining)
            .unlimited(unlimited)
            .canProcess(unlimited || user.getReceiptsProcessed() < user.getReceiptLimit())
            .build();
    }

    // -------------------------------------------------------------------------
    // Admin operations
    // -------------------------------------------------------------------------

    @Transactional
    public ReceiptQuotaInfo resetUsage(UUID userId) {
        userRepository.resetReceiptsProcessed(userId);
        return getQuota(loadUser(userId));
    }

    @Transactional
    public ReceiptQuotaInfo setLimit(UUID userId, int newLimit) {
        if (newLimit < 0) {
            throw new IllegalArgumentException("Receipt limit must be zero or positive");
        }
        User user = loadUser(userId);
        user.setReceiptLimit(newLimit);
        userRepository.save(user);
        return getQuota(user);
    }

    @Transactional
    public ReceiptQuotaInfo setUnlimited(UUID userId, boolean unlimited) {
        User user = loadUser(userId);
        user.setUnlimitedReceipts(unlimited);
        userRepository.save(user);
        return getQuota(user);
    }

    /** Bulk reset of every user's counter — useful for periodic refresh jobs. */
    @Transactional
    public int bulkResetAll() {
        int affected = userRepository.bulkResetReceiptsProcessed();
        log.info("Bulk-reset receipt counters for {} users", affected);
        return affected;
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
