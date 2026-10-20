package com.flowsight.ml.recurring;

import com.flowsight.analytics.LeakDetectionService;
import com.flowsight.analytics.MerchantNormalizationService;
import com.flowsight.dto.leak.LeakInsight;
import com.flowsight.dto.leak.LeakItem;
import com.flowsight.entity.RecurringPattern;
import com.flowsight.entity.RecurringPeriod;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatternLeakDetectorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 6, 20);
    private final MerchantNormalizationService normalizer = new MerchantNormalizationService();
    private final PatternLeakDetector detector =
        new PatternLeakDetector(normalizer::normalizeKey, LeakDetectionService::severity);

    private static Transaction debit(String merchant, String amount, LocalDate date, TransactionCategory category) {
        return Transaction.builder().merchant(merchant).amount(new BigDecimal(amount)).type(TransactionType.DEBIT)
            .category(category).transactionDate(date).build();
    }

    private RecurringPattern pattern(String merchant, String amount, int charges) {
        return RecurringPattern.builder().merchant(merchant).normalizedKey(normalizer.normalizeKey(merchant))
            .period(RecurringPeriod.MONTHLY).estimatedAmount(new BigDecimal(amount)).occurrenceCount(6)
            .probability(new BigDecimal("0.9900")).modelVersion("logreg-1").chargesPerPeriod(charges).build();
    }

    @Test
    void aMonthlyPriceRiseIsFoundWithTwoPaymentsAtTheNewPrice() {
        List<Transaction> txns = new ArrayList<>();
        String[] amounts = {"499", "499", "499", "499", "649", "649"};
        for (int i = 0; i < amounts.length; i++) {
            txns.add(debit("Netflix", amounts[i], AS_OF.minusMonths(amounts.length - 1 - i), TransactionCategory.ENTERTAINMENT));
        }
        LeakInsight creep = detector.creep(List.of(pattern("Netflix", "649", 1)), txns, AS_OF);

        assertThat(creep.getMonthlyImpact()).isEqualByComparingTo("150");
        LeakItem item = creep.getItems().get(0);
        assertThat(item.getEvidence().getMethod()).isEqualTo(PatternLeakDetector.METHOD_CHANGE_POINT);
        assertThat(item.getEvidence().getInputs()).containsEntry("paymentsAfter", 2).containsEntry("priceBefore", 499.0);
        assertThat(item.getEvidence().getWindowStart()).isEqualTo(AS_OF.minusMonths(13));
        assertThat(creep.getEvidence().getSource()).isEqualTo("MODEL");
    }

    @Test
    void aServiceBilledOnTwoAccountsIsADuplicate() {
        RecurringPattern twice = pattern("Netflix", "1298", 2);
        LeakInsight dupes = detector.duplicates(List.of(twice), List.of(), AS_OF);

        assertThat(dupes.getTitle()).isEqualTo("1 service billed more than once");
        assertThat(dupes.getMonthlyImpact()).isEqualByComparingTo("649");
        assertThat(dupes.getItems()).singleElement()
            .satisfies(i -> assertThat(i.getEvidence().getMethod()).isEqualTo(PatternLeakDetector.METHOD_PARALLEL));
    }

    @Test
    void overlapCountsEachCanonicalMerchantOnceAndTitlesReadNaturally() {
        List<Transaction> txns = List.of(
            debit("Notion", "800", AS_OF.minusDays(5), TransactionCategory.SUBSCRIPTIONS),
            debit("Dropbox", "900", AS_OF.minusDays(7), TransactionCategory.SUBSCRIPTIONS),
            debit("Canva", "500", AS_OF.minusDays(9), TransactionCategory.SUBSCRIPTIONS));
        RecurringPattern annualNotion = pattern("Notion", "8000", 1);
        annualNotion.setPeriod(RecurringPeriod.ANNUAL);
        LeakInsight dupes = detector.duplicates(
            List.of(pattern("Notion", "800", 1), annualNotion, pattern("Dropbox", "900", 1), pattern("Canva", "500", 1)),
            txns, AS_OF);

        // Notion monthly and annual: same merchant key twice, then 3 distinct services in Subscriptions
        assertThat(dupes.getTitle()).isEqualTo("1 service billed more than once · 3 subscriptions");
        assertThat(dupes.getItems()).filteredOn(i -> i.getEvidence().getMethod().equals(PatternLeakDetector.METHOD_OVERLAP))
            .hasSize(3);
        assertThat(PatternLeakDetector.overlapTitle(2, TransactionCategory.ENTERTAINMENT)).isEqualTo("2 entertainment subscriptions");
    }

    @Test
    void drainsUseThePersonalBaselineAndSkipSubscriptionsAndNecessities() {
        List<Transaction> txns = new ArrayList<>();
        for (int d = 0; d < 40; d++) {
            txns.add(debit("Starbucks", "6.50", AS_OF.minusDays(2 * d), TransactionCategory.FOOD_DINING));
            txns.add(debit("Kroger", "9", AS_OF.minusDays(2 * d), TransactionCategory.GROCERIES));
            txns.add(debit("Spotify", "5", AS_OF.minusDays(2 * d), TransactionCategory.ENTERTAINMENT));
        }
        SpendBaseline personal = new SpendBaseline(new BigDecimal("40"), new BigDecimal("30"), true);
        LeakInsight drains = detector.drains(txns, List.of(pattern("Spotify", "5", 1)), personal, AS_OF);

        assertThat(drains.getItems()).extracting(LeakItem::getMerchant).containsExactly("Starbucks");
        assertThat(drains.getEvidence().getMethod()).isEqualTo(PatternLeakDetector.METHOD_DRAIN);
        // the INR rule bar of 1500 a month hides the same habit
        assertThat(detector.drains(txns, List.of(), SpendBaseline.global(), AS_OF)).isNull();
    }
}
