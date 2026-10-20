package com.flowsight.analytics;

import com.flowsight.dto.insights.BehavioralPattern;
import com.flowsight.dto.insights.BehavioralProfile;
import com.flowsight.dto.insights.CategoryDrift;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import com.flowsight.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BehavioralAnalysisServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private final BehavioralAnalysisService service = new BehavioralAnalysisService(mock(TransactionRepository.class));

    @Test
    void transfersAreNotSpend() {
        List<Transaction> txns = new ArrayList<>();
        LocalDate start = TODAY.minusDays(90);
        for (LocalDate d = start; !d.isAfter(TODAY); d = d.plusDays(1)) {
            txns.add(tx(d, 500, TransactionCategory.GROCERIES));
            if (isWeekend(d)) txns.add(tx(d, 20000, TransactionCategory.TRANSFER));
        }

        BehavioralProfile p = service.profile(txns, start, TODAY);

        assertThat(find(p, "WEEKEND_OVERSPEND")).isEmpty();
        assertThat(find(p, "CATEGORY_CONCENTRATION")).hasValueSatisfying(c ->
            assertThat(c.getTitle()).startsWith("Groceries"));
    }

    @Test
    void shortHistorySkipsTrendAndDriftChecks() {
        List<Transaction> txns = new ArrayList<>();
        LocalDate start = TODAY.minusDays(60);
        for (int i = 0; i <= 60; i++) txns.add(tx(start.plusDays(i), i < 30 ? 100 : 900, TransactionCategory.SHOPPING));

        BehavioralProfile p = service.profile(txns, start, TODAY);

        assertThat(p.getHistoryDays()).isEqualTo(60);
        assertThat(find(p, "LIFESTYLE_INFLATION")).isEmpty();
        assertThat(find(p, "INCREASING_FREQUENCY")).isEmpty();
        assertThat(p.getNeedsMoreHistory()).contains("LIFESTYLE_INFLATION", "CATEGORY_DRIFT")
            .doesNotContain("WEEKEND_OVERSPEND");
        assertThat(p.getDrifts()).isEmpty();
    }

    @Test
    void lifestyleInflationThresholdFollowsTheUsersOwnVariability() {
        // same 13% growth in both; only the steady spender's growth is beyond their normal swings
        BehavioralProfile steady = service.profile(
            monthly(30000, 30000, 30000, 34000, 34000, 34000), TODAY.minusDays(200), TODAY);
        BehavioralProfile erratic = service.profile(
            monthly(10000, 50000, 30000, 20000, 60000, 22000), TODAY.minusDays(200), TODAY);

        BehavioralPattern p = find(steady, "LIFESTYLE_INFLATION").orElseThrow();
        assertThat(p.getThreshold()).isEqualByComparingTo("10.0");
        assertThat(p.getValue()).isEqualByComparingTo("13.3");
        // back under the user's own threshold: 34,000 - 30,000 * 1.10
        assertThat(p.getMonthlySavingEstimate()).isEqualByComparingTo("1000.00");
        assertThat(find(erratic, "LIFESTYLE_INFLATION")).isEmpty();
    }

    @Test
    void weekendThresholdFollowsWeekToWeekConsistency() {
        LocalDate start = TODAY.minusDays(90);
        BehavioralProfile steady = service.profile(weekendFixture(start, 1200, 1200), start, TODAY);
        BehavioralProfile erratic = service.profile(weekendFixture(start, 2400, 0), start, TODAY);

        BehavioralPattern p = find(steady, "WEEKEND_OVERSPEND").orElseThrow();
        assertThat(p.getValue()).isEqualByComparingTo("140.0");
        assertThat(p.getThreshold()).isEqualByComparingTo("25.0");
        // (1,200 - 500 * 1.25) per weekend day * 104/12 weekend days a month
        assertThat(p.getMonthlySavingEstimate()).isEqualByComparingTo("4983.33");
        assertThat(find(erratic, "WEEKEND_OVERSPEND")).isEmpty();
    }

    @Test
    void sustainedCategoryShiftIsReportedAsDrift() {
        LocalDate firstDay = TODAY.minusDays(181);
        List<Transaction> txns = new ArrayList<>();
        for (int w = 0; w < 26; w++) {
            txns.add(tx(firstDay.plusDays(7L * w + 3), w >= 18 ? 3000 : 1000, TransactionCategory.GROCERIES));
            txns.add(tx(firstDay.plusDays(7L * w + 4), 800, TransactionCategory.FOOD_DINING));
        }

        BehavioralProfile p = service.profile(txns, TODAY.minusDays(190), TODAY);

        assertThat(p.getDrifts()).hasSize(1);
        CategoryDrift d = p.getDrifts().get(0);
        assertThat(d.getCategory()).isEqualTo("GROCERIES");
        assertThat(d.getDirection()).isEqualTo("UP");
        assertThat(d.getSince()).isEqualTo(firstDay.plusDays(7L * 18));
        assertThat(d.getWeeksSince()).isEqualTo(8);
        assertThat(d.getMonthlyChange()).isEqualByComparingTo("8666.67");
        BehavioralPattern drift = find(p, "CATEGORY_DRIFT").orElseThrow();
        assertThat(drift.getMonthlySavingEstimate()).isEqualByComparingTo("8666.67");
        assertThat(drift.getCategory()).isEqualTo("GROCERIES");
    }

    private List<Transaction> monthly(double... amounts) {
        // oldest first, one purchase in the middle of each 30-day bucket
        List<Transaction> txns = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            txns.add(tx(TODAY.minusDays(15 + 30L * (amounts.length - 1 - i)), amounts[i], TransactionCategory.SHOPPING));
        }
        return txns;
    }

    private List<Transaction> weekendFixture(LocalDate start, double evenWeekend, double oddWeekend) {
        List<Transaction> txns = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(TODAY); d = d.plusDays(1)) {
            if (!isWeekend(d)) {
                txns.add(tx(d, 500, TransactionCategory.FOOD_DINING));
            } else {
                long week = java.time.temporal.ChronoUnit.WEEKS.between(start, d);
                double amount = week % 2 == 0 ? evenWeekend : oddWeekend;
                if (amount > 0) txns.add(tx(d, amount, TransactionCategory.ENTERTAINMENT));
            }
        }
        return txns;
    }

    private static boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private static Optional<BehavioralPattern> find(BehavioralProfile p, String code) {
        return p.getPatterns().stream().filter(x -> code.equals(x.getCode())).findFirst();
    }

    private static Transaction tx(LocalDate date, double amount, TransactionCategory category) {
        return Transaction.builder()
            .transactionDate(date)
            .amount(BigDecimal.valueOf(amount))
            .category(category)
            .type(TransactionType.DEBIT)
            .build();
    }
}
