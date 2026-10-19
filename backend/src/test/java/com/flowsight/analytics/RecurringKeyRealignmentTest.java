package com.flowsight.analytics;

import com.flowsight.dto.recurring.RecurringPatternResponse;
import com.flowsight.entity.*;
import com.flowsight.repository.RecurringPatternRepository;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Patterns saved before the token-boundary/cleaning change carry the old keys; the next
// refresh must move them onto today's keys instead of orphaning them.
class RecurringKeyRealignmentTest {

    private final TransactionRepository      transactions = mock(TransactionRepository.class);
    private final RecurringPatternRepository patterns     = mock(RecurringPatternRepository.class);
    private final UserRepository             users        = mock(UserRepository.class);
    private final MerchantNormalizationService normalizer = new MerchantNormalizationService();
    private final List<RecurringPattern>     saved        = new ArrayList<>();

    private final User user = User.builder().id(UUID.randomUUID()).email("r@example.com")
        .passwordHash("x").role(Role.USER).build();

    private RecurringDetectionService service;

    @BeforeEach
    void setUp() {
        service = new RecurringDetectionService(transactions, patterns, users, normalizer);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(patterns.save(any())).thenAnswer(inv -> {
            RecurringPattern p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            saved.add(p);
            return p;
        });
    }

    @Test
    void keptPatternsMoveToTheCurrentKeys() {
        List<Transaction> txns = new ArrayList<>();
        for (int m = 4; m >= 1; m--) {
            txns.add(debit("STARBUCKS COFFEE", "450", LocalDate.now().minusMonths(m)));
            txns.add(debit("DOMINOS PIZZA", "600", LocalDate.now().minusMonths(m).plusDays(3)));
        }
        when(transactions.findForRecurringDetection(eq(user.getId()), any())).thenReturn(txns);
        // keys as the previous normalizer produced them (no alias, first two words)
        RecurringPattern confirmed = stored("STARBUCKS COFFEE", "starbuckscoffee", true, false);
        RecurringPattern dismissed = stored("DOMINOS PIZZA", "dominospizza", false, true);
        when(patterns.findByUserIdOrderByConfidenceDesc(user.getId())).thenReturn(List.of(confirmed, dismissed));

        List<RecurringPatternResponse> result = service.detectAndRefresh(user.getId());

        assertThat(confirmed.getNormalizedKey()).isEqualTo("starbucks").isEqualTo(normalizer.normalizeKey("STARBUCKS COFFEE"));
        assertThat(dismissed.getNormalizedKey()).isEqualTo("dominos");
        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.getId()).isEqualTo(confirmed.getId());
            assertThat(r.isUserConfirmed()).isTrue();
            assertThat(r.getOccurrenceCount()).isEqualTo(4);
        });
        assertThat(saved).allSatisfy(p -> assertThat(p.getId()).isIn(confirmed.getId(), dismissed.getId()));

        // the leak join keys transactions the same way
        assertThat(normalizer.normalizeKey(txns.get(0).getMerchant())).isEqualTo(confirmed.getNormalizedKey());
        assertThat(normalizer.normalizeKey("Starbucks")).isEqualTo(confirmed.getNormalizedKey());
    }

    @Test
    void aKeyAlreadyHeldByAnotherKeptPatternIsLeftAlone() {
        when(transactions.findForRecurringDetection(eq(user.getId()), any())).thenReturn(List.of());
        RecurringPattern current = stored("Starbucks", "starbucks", true, false);
        RecurringPattern old = stored("STARBUCKS COFFEE", "starbuckscoffee", false, true);
        when(patterns.findByUserIdOrderByConfidenceDesc(user.getId())).thenReturn(List.of(current, old));

        service.detectAndRefresh(user.getId());

        assertThat(current.getNormalizedKey()).isEqualTo("starbucks");
        assertThat(old.getNormalizedKey()).isEqualTo("starbuckscoffee");
    }

    private RecurringPattern stored(String merchant, String key, boolean confirmed, boolean dismissed) {
        return RecurringPattern.builder()
            .id(UUID.randomUUID())
            .user(user)
            .merchant(merchant)
            .normalizedKey(key)
            .period(RecurringPeriod.MONTHLY)
            .estimatedAmount(new BigDecimal("450.0000"))
            .occurrenceCount(3)
            .confidence(new BigDecimal("0.800"))
            .isUserConfirmed(confirmed)
            .isDismissed(dismissed)
            .build();
    }

    private Transaction debit(String merchant, String amount, LocalDate date) {
        return Transaction.builder()
            .id(UUID.randomUUID())
            .user(user)
            .amount(new BigDecimal(amount))
            .transactionDate(date)
            .description(merchant)
            .merchant(merchant)
            .category(TransactionCategory.FOOD_DINING)
            .type(TransactionType.DEBIT)
            .source(TransactionSource.CSV)
            .build();
    }
}
