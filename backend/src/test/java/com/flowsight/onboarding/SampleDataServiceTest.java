package com.flowsight.onboarding;

import com.flowsight.analytics.RecurringDetectionService;
import com.flowsight.exception.FlowsightException;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.security.RateLimiter;
import com.flowsight.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SampleDataServiceTest {

    @Mock private SampleTransactionRepository sampleTransactions;
    @Mock private TransactionRepository       transactionRepository;
    @Mock private OnboardingUserRepository    users;
    @Mock private RecurringDetectionService   recurringDetection;
    @Mock private RateLimiter                 rateLimiter;
    @Mock private AuditLogService             auditLog;
    @InjectMocks private SampleDataService service;

    @Test
    void loadAndRemoveUseTheirOwnPerUserBucket() {
        UUID userId = UUID.randomUUID();
        doThrow(new FlowsightException("slow down", HttpStatus.TOO_MANY_REQUESTS))
            .when(rateLimiter).check("sample-data:" + userId, 10, Duration.ofHours(1));

        assertThatThrownBy(() -> service.load(userId)).isInstanceOf(FlowsightException.class);
        assertThatThrownBy(() -> service.remove(userId)).isInstanceOf(FlowsightException.class);

        verifyNoInteractions(users, sampleTransactions, transactionRepository, recurringDetection, auditLog);
    }

    @Test
    void statusIsNotRateLimited() {
        UUID userId = UUID.randomUUID();
        service.status(userId);
        verify(sampleTransactions).countByUserIdAndSource(userId, com.flowsight.entity.TransactionSource.SAMPLE);
        verifyNoInteractions(rateLimiter);
    }
}
