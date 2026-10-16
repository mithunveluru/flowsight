package com.flowsight.onboarding;

import com.flowsight.analytics.RecurringDetectionService;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionSource;
import com.flowsight.entity.User;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.security.RateLimiter;
import com.flowsight.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Seeds and removes the synthetic history behind "Explore with sample data". */
@Service
@RequiredArgsConstructor
@Slf4j
public class SampleDataService {

    static final String ACTION_LOADED  = "SAMPLE_DATA_LOADED";
    static final String ACTION_REMOVED = "SAMPLE_DATA_REMOVED";
    static final int      RATE_LIMIT  = 10;
    static final Duration RATE_WINDOW = Duration.ofHours(1);

    private final SampleTransactionRepository sampleTransactions;
    private final TransactionRepository       transactionRepository;
    private final OnboardingUserRepository    users;
    private final RecurringDetectionService   recurringDetection;
    private final RateLimiter                 rateLimiter;
    private final AuditLogService             auditLog;

    @Transactional(readOnly = true)
    public SampleDataStatus status(UUID userId) {
        return SampleDataStatus.of(sampleTransactions.countByUserIdAndSource(userId, TransactionSource.SAMPLE));
    }

    @Transactional
    public SampleDataStatus load(UUID userId) {
        rateLimiter.check(bucket(userId), RATE_LIMIT, RATE_WINDOW);
        User user = lockUser(userId);

        long existing = sampleTransactions.countByUserIdAndSource(userId, TransactionSource.SAMPLE);
        if (existing > 0) return SampleDataStatus.of(existing);

        List<Transaction> rows = SampleDataGenerator.generate(user, LocalDate.now());
        transactionRepository.saveAll(rows);
        // subscriptions and money drains read stored recurring patterns
        recurringDetection.detectAndRefresh(userId);

        auditLog.log(user, ACTION_LOADED, "Transactions", null, Map.of("count", rows.size()));
        log.info("Sample data loaded for user {}: {} transactions", userId, rows.size());
        return SampleDataStatus.of(rows.size());
    }

    @Transactional
    public SampleDataStatus remove(UUID userId) {
        rateLimiter.check(bucket(userId), RATE_LIMIT, RATE_WINDOW);
        User user = lockUser(userId);

        int removed = sampleTransactions.deleteByUserIdAndSource(userId, TransactionSource.SAMPLE);
        if (removed > 0) {
            // drop patterns that only existed because of the sample rows
            recurringDetection.detectAndRefresh(userId);
            auditLog.log(user, ACTION_REMOVED, "Transactions", null, Map.of("count", removed));
            log.info("Sample data removed for user {}: {} transactions", userId, removed);
        }
        return SampleDataStatus.of(0);
    }

    private User lockUser(UUID userId) {
        return users.findWithLockById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    static String bucket(UUID userId) {
        return "sample-data:" + userId;
    }
}
