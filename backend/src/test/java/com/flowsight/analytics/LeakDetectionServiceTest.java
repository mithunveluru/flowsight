package com.flowsight.analytics;

import com.flowsight.dto.leak.LeakDetectionResponse;
import com.flowsight.dto.leak.LeakInsight;
import com.flowsight.entity.RecurringPattern;
import com.flowsight.entity.RecurringPeriod;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import com.flowsight.repository.RecurringPatternRepository;
import com.flowsight.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Patterns store the canonical display name ("Netflix"); transactions keep the raw merchant ("NETFLIX.COM").
class LeakDetectionServiceTest {

    private final MerchantNormalizationService normalizer = new MerchantNormalizationService();
    private final LocalDate today = LocalDate.now();

    @Test
    void duplicateSubscriptionsAreFoundThroughRenamedMerchants() {
        List<Transaction> txns = new ArrayList<>();
        for (int m = 0; m < 3; m++) {
            txns.add(debit("NETFLIX.COM", "649", today.minusMonths(m)));
            txns.add(debit("SPOTIFY INDIA", "119", today.minusMonths(m)));
        }

        LeakDetectionResponse r = detect(txns, List.of(
            pattern("Netflix", "NETFLIX.COM", RecurringPeriod.MONTHLY, "649"),
            pattern("Spotify", "SPOTIFY INDIA", RecurringPeriod.MONTHLY, "119")));

        LeakInsight dupes = find(r, "DUPLICATE_SUBSCRIPTIONS");
        assertThat(dupes.getAffectedItemsCount()).isEqualTo(2);
        assertThat(dupes.getMonthlyImpact()).isEqualByComparingTo("119");
    }

    @Test
    void subscriptionCreepIsFoundThroughRenamedMerchants() {
        List<Transaction> txns = new ArrayList<>();
        String[] amounts = {"199", "199", "199", "199", "249", "249"};
        for (int i = 0; i < amounts.length; i++) {
            txns.add(debit("CULTFIT*MEMBERSHIP", amounts[i], today.minusWeeks(amounts.length - 1 - i)));
        }

        LeakDetectionResponse r = detect(txns, List.of(
            pattern("Cult.fit", "CULTFIT*MEMBERSHIP", RecurringPeriod.WEEKLY, "249")));

        LeakInsight creep = find(r, "SUBSCRIPTION_CREEP");
        // (249 - 199) x 52 / 12
        assertThat(creep.getMonthlyImpact()).isEqualByComparingTo("216.67");
    }

    private LeakDetectionResponse detect(List<Transaction> txns, List<RecurringPattern> patterns) {
        TransactionRepository txRepo = mock(TransactionRepository.class);
        RecurringPatternRepository patternRepo = mock(RecurringPatternRepository.class);
        when(txRepo.findForRecurringDetection(any(), any())).thenReturn(txns);
        when(patternRepo.findByUserIdAndIsDismissedFalseOrderByEstimatedAmountDesc(any())).thenReturn(patterns);
        return new LeakDetectionService(txRepo, patternRepo, normalizer).detectLeaks(UUID.randomUUID());
    }

    // mirrors RecurringDetectionService: display name from the alias, key from the raw merchant
    private RecurringPattern pattern(String displayName, String rawMerchant, RecurringPeriod period, String amount) {
        return RecurringPattern.builder().merchant(displayName).normalizedKey(normalizer.normalizeKey(rawMerchant))
            .period(period).estimatedAmount(new BigDecimal(amount)).occurrenceCount(3).build();
    }

    private static Transaction debit(String merchant, String amount, LocalDate date) {
        return Transaction.builder().merchant(merchant).amount(new BigDecimal(amount)).type(TransactionType.DEBIT)
            .category(TransactionCategory.SUBSCRIPTIONS).transactionDate(date).build();
    }

    private static LeakInsight find(LeakDetectionResponse r, String type) {
        return r.getLeaks().stream().filter(l -> type.equals(l.getType())).findFirst()
            .orElseThrow(() -> new AssertionError(type + " not detected; got " + r.getLeaks()));
    }
}
