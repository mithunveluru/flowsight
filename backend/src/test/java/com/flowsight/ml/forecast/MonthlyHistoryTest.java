package com.flowsight.ml.forecast;

import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyHistoryTest {

    static MonthlyHistory.Txn debit(String date, TransactionCategory c, double amount) {
        return new MonthlyHistory.Txn(LocalDate.parse(date), TransactionType.DEBIT, c, amount);
    }

    static MonthlyHistory.Txn credit(String date, double amount) {
        return new MonthlyHistory.Txn(LocalDate.parse(date), TransactionType.CREDIT, TransactionCategory.INCOME, amount);
    }

    @Test
    void splitsCompleteMonthsFromTheMonthSoFar() {
        List<MonthlyHistory.Txn> txns = List.of(
            credit("2026-07-01", 50000), debit("2026-07-03", TransactionCategory.GROCERIES, 2000),
            debit("2026-07-20", TransactionCategory.GROCERIES, 3000),
            credit("2026-08-01", 50000), debit("2026-08-25", TransactionCategory.TRAVEL, 9000),
            credit("2026-09-01", 50000), debit("2026-09-05", TransactionCategory.GROCERIES, 1500),
            debit("2026-09-30", TransactionCategory.GROCERIES, 999));
        MonthlyHistory h = MonthlyHistory.of(txns, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-09-10"));

        assertThat(h.months()).containsExactly(YearMonth.of(2026, 7), YearMonth.of(2026, 8));
        assertThat(h.income()).containsExactly(50000, 50000);
        assertThat(h.spend()).containsExactly(5000, 9000);
        assertThat(h.categories().get(TransactionCategory.GROCERIES)).containsExactly(5000, 0);
        assertThat(h.categories().get(TransactionCategory.TRAVEL)).containsExactly(0, 9000);
        assertThat(h.incomeToDate()).isEqualTo(50000);
        assertThat(h.spendToDate()).isEqualTo(1500);   // the 30th is after today
        assertThat(h.incomeSameDays()).isEqualTo(100000);
        assertThat(h.categoriesSameDays()).containsEntry(TransactionCategory.GROCERIES, 2000.0);
        assertThat(h.net()).containsExactly(45000, 41000);
    }

    @Test
    void aFirstMonthStartingAfterTheOpeningWeekIsNotComplete() {
        List<MonthlyHistory.Txn> txns = List.of(debit("2026-08-12", TransactionCategory.SHOPPING, 700));
        MonthlyHistory h = MonthlyHistory.of(txns, LocalDate.parse("2026-08-12"), LocalDate.parse("2026-09-03"));
        assertThat(h.dataMonths()).isZero();
        assertThat(h.spendToDate()).isZero();
        assertThat(h.categories()).isEmpty();

        MonthlyHistory early = MonthlyHistory.of(txns, LocalDate.parse("2026-08-05"), LocalDate.parse("2026-09-03"));
        assertThat(early.months()).containsExactly(YearMonth.of(2026, 8));
    }

    @Test
    void keepsAtMostTwentyFourMonths() {
        MonthlyHistory h = MonthlyHistory.of(List.of(), LocalDate.parse("2020-01-01"), LocalDate.parse("2026-09-03"));
        assertThat(h.dataMonths()).isEqualTo(24);
        assertThat(h.months().get(0)).isEqualTo(YearMonth.of(2024, 9));
        assertThat(h.months().get(23)).isEqualTo(YearMonth.of(2026, 8));
    }
}
