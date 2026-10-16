package com.flowsight.onboarding;

import com.flowsight.analytics.LeakDetectionService;
import com.flowsight.dto.leak.LeakInsight;
import com.flowsight.entity.Role;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionSource;
import com.flowsight.entity.TransactionType;
import com.flowsight.entity.User;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.repository.AuditLogRepository;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SampleDataServiceIntegrationTest {

    @Autowired private SampleDataService           service;
    @Autowired private SampleTransactionRepository sampleTransactions;
    @Autowired private TransactionRepository       transactions;
    @Autowired private UserRepository              userRepository;
    @Autowired private AuditLogRepository          auditLogs;
    @Autowired private LeakDetectionService        leakDetection;

    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        user  = userRepository.save(newUser());
        other = userRepository.save(newUser());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllById(List.of(user.getId(), other.getId()));
    }

    @Test
    void loadingTwiceDoesNotDuplicateRows() {
        SampleDataStatus first = service.load(user.getId());
        SampleDataStatus second = service.load(user.getId());

        assertThat(first.present()).isTrue();
        assertThat(first.transactionCount()).isGreaterThan(100);
        assertThat(second).isEqualTo(first);
        assertThat(sampleCount(user)).isEqualTo(first.transactionCount());
        assertThat(service.status(user.getId())).isEqualTo(first);
    }

    @Test
    void removeDeletesOnlyThisUsersSampleRows() {
        transactions.save(manual(user));
        service.load(user.getId());
        long otherSample = service.load(other.getId()).transactionCount();

        SampleDataStatus after = service.remove(user.getId());

        assertThat(after).isEqualTo(new SampleDataStatus(false, 0));
        assertThat(sampleCount(user)).isZero();
        assertThat(transactions.countByUserId(user.getId())).isEqualTo(1);
        assertThat(sampleCount(other)).isEqualTo(otherSample);
        assertThat(service.remove(user.getId())).isEqualTo(new SampleDataStatus(false, 0));
    }

    @Test
    void statusIsScopedToTheCaller() {
        service.load(other.getId());

        assertThat(service.status(user.getId())).isEqualTo(new SampleDataStatus(false, 0));
        assertThat(service.status(other.getId()).present()).isTrue();
        assertThatThrownBy(() -> service.load(UUID.randomUUID()))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void stateChangesAreAuditLogged() {
        service.load(user.getId());
        service.load(user.getId());
        service.remove(user.getId());

        List<String> actions = auditLogs.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 10))
            .map(a -> a.getAction()).getContent();
        assertThat(actions).containsExactly(SampleDataService.ACTION_REMOVED, SampleDataService.ACTION_LOADED);
    }

    @Test
    void sampleHistoryGivesTheDrainRulesSomethingToFind() {
        service.load(user.getId());

        List<String> types = leakDetection.detectLeaks(user.getId()).getLeaks().stream()
            .map(LeakInsight::getType).toList();
        assertThat(types).contains("SUBSCRIPTION_CREEP", "DUPLICATE_SUBSCRIPTIONS", "HIGH_FREQUENCY_SMALL_SPEND");
    }

    private long sampleCount(User u) {
        return sampleTransactions.countByUserIdAndSource(u.getId(), TransactionSource.SAMPLE);
    }

    private static Transaction manual(User u) {
        return Transaction.builder()
            .user(u)
            .amount(new BigDecimal("250.00"))
            .transactionDate(LocalDate.now())
            .description("Lunch")
            .merchant("Cafe")
            .category(TransactionCategory.FOOD_DINING)
            .type(TransactionType.DEBIT)
            .source(TransactionSource.MANUAL)
            .build();
    }

    private static User newUser() {
        return User.builder()
            .email("onboarding-" + UUID.randomUUID() + "@example.com")
            .passwordHash("x")
            .role(Role.USER)
            .build();
    }
}
