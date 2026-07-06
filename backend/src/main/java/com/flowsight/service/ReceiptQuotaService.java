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

// Single authority for the receipt OCR quota. Check runs before any OCR;
// the increment is an atomic UPDATE so concurrent uploads can't overshoot.
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptQuotaService {

    private final UserRepository userRepository;

    // throws QuotaExceededException when out of quota; call before any OCR
    public void requireQuotaAvailable(User user) {
        if (user.isUnlimitedReceipts()) return;
        if (user.getReceiptsProcessed() < user.getReceiptLimit()) return;

        throw new QuotaExceededException(String.format(
            "You've reached your receipt processing limit of %d. " +
            "Your existing receipts and analytics remain fully accessible — only new receipt OCR is blocked.",
            user.getReceiptLimit()
        ));
    }

    // atomic increment; call after OCR ran (success or failure)
    @Transactional
    public void recordReceiptProcessed(UUID userId) {
        int updated = userRepository.incrementReceiptsProcessed(userId);
        if (updated != 1) {
            log.warn("Receipt counter increment touched {} rows for user {}", updated, userId);
        }
    }

    public ReceiptQuotaInfo getQuota(UUID userId) {
        return getQuota(loadUser(userId));
    }

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

    // bulk reset (periodic refresh job)
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
