package com.flowsight.analytics.simulation;

import com.flowsight.dto.simulation.FinancialBaseline;
import com.flowsight.entity.RecurringPattern;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import com.flowsight.repository.RecurringPatternRepository;
import com.flowsight.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

// Current monthly financial baseline from the last 3 complete calendar months of transactions.
@Service
@RequiredArgsConstructor
public class BaselineCalculator {

    private static final int LOOKBACK_MONTHS        = 3;
    private static final int FIRST_MONTH_GRACE_DAYS = 7;

    private final TransactionRepository      transactionRepository;
    private final RecurringPatternRepository recurringPatternRepository;

    public FinancialBaseline compute(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate to    = today.withDayOfMonth(1).minusDays(1);
        LocalDate from  = coveredFrom(userId, today.minusMonths(LOOKBACK_MONTHS).withDayOfMonth(1));
        int dataMonths  = from.isAfter(to) ? 0 : (int) ChronoUnit.MONTHS.between(from, to.plusDays(1));

        BigDecimal monthlyIncome = BigDecimal.ZERO;
        BigDecimal monthlySpend  = BigDecimal.ZERO;
        if (dataMonths > 0) {
            monthlyIncome = perMonth(transactionRepository.sumByTypeAndDateRange(
                userId, TransactionType.CREDIT, from, to), dataMonths);
            monthlySpend  = perMonth(transactionRepository.sumByTypeAndDateRange(
                userId, TransactionType.DEBIT,  from, to), dataMonths);
        }

        // recurring: sum of monthly equivalents from active patterns
        List<RecurringPattern> patterns = recurringPatternRepository
            .findByUserIdAndIsDismissedFalseOrderByEstimatedAmountDesc(userId);
        BigDecimal monthlyRecurring = patterns.stream()
            .filter(p -> p.getEstimatedAmount() != null)
            .map(p -> p.getEstimatedAmount()
                .multiply(BigDecimal.valueOf(p.getPeriod().getAnnualFrequency()))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // recurring debits are already part of monthlySpend
        BigDecimal monthlyDiscretionary = monthlyIncome.subtract(monthlySpend).max(BigDecimal.ZERO);

        BigDecimal monthlyNetSavings = monthlyIncome.subtract(monthlySpend);

        double savingsRate = monthlyIncome.compareTo(BigDecimal.ZERO) > 0
            ? monthlyNetSavings.doubleValue() / monthlyIncome.doubleValue()
            : 0.0;

        List<Object[]> categoryRows = dataMonths == 0 ? List.of() : transactionRepository.categoryBreakdown(
            userId, TransactionType.DEBIT, from, to);
        String topCategoryName = null;
        BigDecimal topCategoryMonthly = BigDecimal.ZERO;
        if (!categoryRows.isEmpty()) {
            Object[] top = categoryRows.get(0); // already sorted DESC by sum
            TransactionCategory cat = (TransactionCategory) top[0];
            BigDecimal amount = top[1] instanceof BigDecimal b ? b : new BigDecimal(top[1].toString());
            topCategoryName    = cat.getDisplayName();
            topCategoryMonthly = perMonth(amount, dataMonths);
        }

        boolean hasEnoughData = dataMonths > 0
                             && (monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                              || monthlySpend.compareTo(BigDecimal.ZERO)  > 0);

        return FinancialBaseline.builder()
            .monthlyIncome(monthlyIncome)
            .monthlySpend(monthlySpend)
            .monthlyRecurring(monthlyRecurring)
            .monthlyDiscretionary(monthlyDiscretionary)
            .monthlyNetSavings(monthlyNetSavings)
            .savingsRate(Math.max(-1.0, Math.min(1.0, savingsRate)))
            .dataMonths(dataMonths)
            .topCategoryName(topCategoryName)
            .topCategoryMonthlySpend(topCategoryMonthly)
            .hasEnoughData(hasEnoughData)
            .build();
    }

    // First day of the first complete month of history inside the window. A first transaction in the
    // opening week still counts that month as complete: statements often start with a few quiet days.
    private LocalDate coveredFrom(UUID userId, LocalDate windowStart) {
        LocalDate earliest = transactionRepository.findEarliestTransactionDate(userId).orElse(null);
        if (earliest == null) return windowStart.plusMonths(LOOKBACK_MONTHS);
        LocalDate firstFull = earliest.getDayOfMonth() <= FIRST_MONTH_GRACE_DAYS
            ? earliest.withDayOfMonth(1)
            : earliest.withDayOfMonth(1).plusMonths(1);
        return firstFull.isAfter(windowStart) ? firstFull : windowStart;
    }

    private static BigDecimal perMonth(BigDecimal total, int months) {
        BigDecimal t = total != null ? total : BigDecimal.ZERO;
        return t.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }
}
