package com.flowsight.analytics.simulation;

import com.flowsight.dto.simulation.FinancialBaseline;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaselineCalculatorTest {

    private final LocalDate today = LocalDate.now();

    // Salary 90,000 on the 1st; rent 25,000 and EMI 15,000 on the 2nd; groceries 20,000 on the 15th.
    private List<Transaction> monthlyHistory(int monthsBack) {
        List<Transaction> txns = new ArrayList<>();
        for (int m = 0; m <= monthsBack; m++) {
            LocalDate first = today.minusMonths(m).withDayOfMonth(1);
            add(txns, first,              "90000", TransactionType.CREDIT, TransactionCategory.INCOME);
            add(txns, first.plusDays(1),  "25000", TransactionType.DEBIT,  TransactionCategory.UTILITIES);
            add(txns, first.plusDays(1),  "15000", TransactionType.DEBIT,  TransactionCategory.FINANCE);
            add(txns, first.plusDays(14), "20000", TransactionType.DEBIT,  TransactionCategory.GROCERIES);
        }
        return txns;
    }

    @Test
    void averagesCompleteMonthsOnly_currentPartialMonthDoesNotInflate() {
        FinancialBaseline b = compute(monthlyHistory(12));

        assertThat(b.getDataMonths()).isEqualTo(3);
        assertThat(b.getMonthlyIncome()).isEqualByComparingTo("90000");
        assertThat(b.getMonthlySpend()).isEqualByComparingTo("60000");
        assertThat(b.getMonthlyNetSavings()).isEqualByComparingTo("30000");
        assertThat(b.getTopCategoryName()).isEqualTo("Utilities");
        assertThat(b.getTopCategoryMonthlySpend()).isEqualByComparingTo("25000");
        assertThat(b.isHasEnoughData()).isTrue();
    }

    @Test
    void discretionaryDoesNotSubtractRecurringTwice() {
        FinancialBaseline b = compute(monthlyHistory(12));

        assertThat(b.getMonthlyRecurring()).isEqualByComparingTo("40000");
        assertThat(b.getMonthlyDiscretionary()).isEqualByComparingTo("30000");
    }

    @Test
    void twoWeeksOfData_hasNoCompleteMonthAndSaysSo() {
        List<Transaction> txns = new ArrayList<>();
        add(txns, today.minusDays(10), "90000", TransactionType.CREDIT, TransactionCategory.INCOME);
        for (int d = 0; d < 14; d++) {
            add(txns, today.minusDays(d), "1500", TransactionType.DEBIT, TransactionCategory.FOOD_DINING);
        }

        FinancialBaseline b = compute(txns);

        assertThat(b.getDataMonths()).isZero();
        assertThat(b.getMonthlyIncome()).isEqualByComparingTo("0");
        assertThat(b.isHasEnoughData()).isFalse();
    }

    @Test
    void historyStartingInFirstWeek_countsThatMonth() {
        List<Transaction> txns = monthlyHistory(2);
        txns.removeIf(t -> t.getTransactionDate().isBefore(today.minusMonths(2).withDayOfMonth(2)));

        FinancialBaseline b = compute(txns);

        assertThat(b.getDataMonths()).isEqualTo(2);
        assertThat(b.getMonthlySpend()).isEqualByComparingTo("60000");
    }

    @Test
    void historyStartingMidMonth_skipsThatPartialMonth() {
        List<Transaction> txns = monthlyHistory(2);
        txns.removeIf(t -> t.getTransactionDate().isBefore(today.minusMonths(2).withDayOfMonth(15)));

        FinancialBaseline b = compute(txns);

        assertThat(b.getDataMonths()).isEqualTo(1);
        assertThat(b.getMonthlyIncome()).isEqualByComparingTo("90000");
        assertThat(b.getMonthlySpend()).isEqualByComparingTo("60000");
    }

    @Test
    void noTransactions_isEmptyBaseline() {
        FinancialBaseline b = compute(List.of());

        assertThat(b.getDataMonths()).isZero();
        assertThat(b.isHasEnoughData()).isFalse();
    }

    private static void add(List<Transaction> out, LocalDate date, String amount,
                            TransactionType type, TransactionCategory category) {
        if (date.isAfter(LocalDate.now())) return;
        out.add(Transaction.builder().transactionDate(date).amount(new BigDecimal(amount))
            .type(type).category(category).build());
    }

    private static boolean within(Transaction t, LocalDate from, LocalDate to) {
        return !t.getTransactionDate().isBefore(from) && !t.getTransactionDate().isAfter(to);
    }

    private static FinancialBaseline compute(List<Transaction> txns) {
        TransactionRepository txRepo = mock(TransactionRepository.class);
        RecurringPatternRepository patternRepo = mock(RecurringPatternRepository.class);

        when(txRepo.findEarliestTransactionDate(any()))
            .thenReturn(txns.stream().map(Transaction::getTransactionDate).min(Comparator.naturalOrder()));
        when(txRepo.sumByTypeAndDateRange(any(), any(), any(), any())).thenAnswer(inv -> {
            TransactionType type = inv.getArgument(1);
            LocalDate from = inv.getArgument(2), to = inv.getArgument(3);
            return txns.stream().filter(t -> t.getType() == type && within(t, from, to))
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        });
        when(txRepo.categoryBreakdown(any(), any(), any(), any())).thenAnswer(inv -> {
            TransactionType type = inv.getArgument(1);
            LocalDate from = inv.getArgument(2), to = inv.getArgument(3);
            Map<TransactionCategory, BigDecimal> sums = txns.stream()
                .filter(t -> t.getType() == type && within(t, from, to))
                .collect(Collectors.groupingBy(Transaction::getCategory,
                    Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));
            return sums.entrySet().stream()
                .sorted(Map.Entry.<TransactionCategory, BigDecimal>comparingByValue().reversed())
                .map(e -> new Object[]{e.getKey(), e.getValue(), 0L})
                .collect(Collectors.toList());
        });
        when(patternRepo.findByUserIdAndIsDismissedFalseOrderByEstimatedAmountDesc(any())).thenReturn(List.of(
            pattern("Rent", "25000"), pattern("Car EMI", "15000")));

        return new BaselineCalculator(txRepo, patternRepo).compute(UUID.randomUUID());
    }

    private static RecurringPattern pattern(String merchant, String amount) {
        return RecurringPattern.builder().merchant(merchant).normalizedKey(merchant.toLowerCase())
            .period(RecurringPeriod.MONTHLY).estimatedAmount(new BigDecimal(amount)).build();
    }
}
