package com.flowsight.analytics;

import com.flowsight.dto.leak.LeakDetectionResponse;
import com.flowsight.dto.leak.LeakInsight;
import com.flowsight.entity.RecurringPattern;
import com.flowsight.entity.RecurringPeriod;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import com.flowsight.ml.PersonalizationService;
import com.flowsight.ml.recurring.PatternLeakDetector;
import com.flowsight.ml.recurring.RecurringScorer;
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

    @Test
    void overlapTitlesDoNotRepeatTheWordSubscriptions() {
        List<Transaction> txns = new ArrayList<>();
        for (String m : new String[]{"Notion", "Dropbox", "Canva"}) txns.add(debit(m, "500", today.minusDays(5)));

        LeakDetectionResponse r = detect(txns, List.of(pattern("Notion", "Notion", RecurringPeriod.MONTHLY, "500"),
            pattern("Dropbox", "Dropbox", RecurringPeriod.MONTHLY, "500"),
            pattern("Canva", "Canva", RecurringPeriod.MONTHLY, "500")));

        assertThat(find(r, "DUPLICATE_SUBSCRIPTIONS").getTitle()).isEqualTo("3 subscriptions");
        assertThat(find(r, "DUPLICATE_SUBSCRIPTIONS").getEvidence().getSource()).isEqualTo("RULES");
    }

    @Test
    void aMonthlyPriceRiseIsFoundOnceOlderPaymentsAreInView() {
        // four monthly payments: the oldest is outside the three-month window the other rules use
        List<Transaction> txns = new ArrayList<>();
        String[] amounts = {"499", "499", "649", "649"};
        for (int i = 0; i < amounts.length; i++) txns.add(debit("Netflix", amounts[i], today.minusMonths(3).minusDays(4).plusMonths(i)));

        LeakDetectionResponse r = detect(txns, List.of(pattern("Netflix", "Netflix", RecurringPeriod.MONTHLY, "649")));

        assertThat(find(r, "SUBSCRIPTION_CREEP").getMonthlyImpact()).isEqualByComparingTo("150");
    }

    @Test
    void theModelPathRunsOnlyForScoredPatternsWithTheCapabilityServing() {
        List<Transaction> txns = new ArrayList<>();
        String[] amounts = {"499", "499", "499", "649", "649"};
        for (int i = 0; i < amounts.length; i++) txns.add(debit("Netflix", amounts[i], today.minusMonths(4 - i)));
        RecurringPattern scored = pattern("Netflix", "Netflix", RecurringPeriod.MONTHLY, "649");
        scored.setModelVersion("logreg-1");

        assertThat(find(detect(txns, List.of(scored), true, false), "SUBSCRIPTION_CREEP").getEvidence().getMethod())
            .isEqualTo(PatternLeakDetector.METHOD_CHANGE_POINT);
        // capability switched off, or patterns stored by the rules: the rules answer
        assertThat(find(detect(txns, List.of(scored), false, false), "SUBSCRIPTION_CREEP").getEvidence().getSource())
            .isEqualTo("RULES");
        assertThat(find(detect(txns, List.of(pattern("Netflix", "Netflix", RecurringPeriod.MONTHLY, "649")), true, false),
            "SUBSCRIPTION_CREEP").getEvidence().getSource()).isEqualTo("RULES");
    }

    @Test
    void drainsUseGlobalThresholdsWhenPersonalizationIsOff() {
        List<Transaction> txns = new ArrayList<>();
        for (int m = 1; m <= 4; m++) txns.add(debit("Oakwood Apartments", "2000", today.minusMonths(m).withDayOfMonth(2)));
        for (int d = 0; d < 30; d++) {
            txns.add(Transaction.builder().merchant("Starbucks").amount(new BigDecimal("6.50")).type(TransactionType.DEBIT)
                .category(TransactionCategory.FOOD_DINING).transactionDate(today.minusDays(2 * d)).build());
        }
        RecurringPattern scored = pattern("Oakwood Apartments", "Oakwood Apartments", RecurringPeriod.MONTHLY, "2000");
        scored.setModelVersion("logreg-1");

        LeakInsight personal = find(detect(txns, List.of(scored), true, true), "HIGH_FREQUENCY_SMALL_SPEND");
        assertThat(personal.getEvidence().getInputs()).containsEntry("personalBaseline", true);
        assertThat(detect(txns, List.of(scored), true, false).getLeaks())
            .noneMatch(l -> l.getType().equals("HIGH_FREQUENCY_SMALL_SPEND"));
    }

    private LeakDetectionResponse detect(List<Transaction> txns, List<RecurringPattern> patterns) {
        return detect(txns, patterns, false, false);
    }

    private LeakDetectionResponse detect(List<Transaction> txns, List<RecurringPattern> patterns, boolean serving,
                                         boolean personalized) {
        TransactionRepository txRepo = mock(TransactionRepository.class);
        RecurringPatternRepository patternRepo = mock(RecurringPatternRepository.class);
        RecurringScorer scorer = mock(RecurringScorer.class);
        PersonalizationService personalization = mock(PersonalizationService.class);
        when(txRepo.findForRecurringDetection(any(), any())).thenReturn(txns);
        when(patternRepo.findByUserIdAndIsDismissedFalseOrderByEstimatedAmountDesc(any())).thenReturn(patterns);
        when(scorer.serving()).thenReturn(serving);
        when(personalization.isEnabled(any())).thenReturn(personalized);
        return new LeakDetectionService(txRepo, patternRepo, normalizer, scorer, personalization)
            .detectLeaks(UUID.randomUUID());
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
