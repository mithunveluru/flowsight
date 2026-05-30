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
import java.util.List;
import java.util.UUID;

/**
 * Computes the user's current monthly financial baseline from their actual data.
 *
 * <p>Income, spend, and category mix are averaged over the last 3 months of transactions.
 * Recurring commitments come from Phase 6 detected patterns. The result feeds every
 * simulation engine — no hardcoded assumptions when the data is sufficient.
 */
@Service
@RequiredArgsConstructor
public class BaselineCalculator {

    private static final int LOOKBACK_MONTHS = 3;

    private final TransactionRepository      transactionRepository;
    private final RecurringPatternRepository recurringPatternRepository;

    public FinancialBaseline compute(UUID userId) {
        LocalDate today    = LocalDate.now();
        LocalDate from     = today.minusMonths(LOOKBACK_MONTHS).withDayOfMonth(1);

        BigDecimal totalIncome = nonNull(transactionRepository.sumByTypeAndDateRange(
            userId, TransactionType.CREDIT, from, today));
        BigDecimal totalSpend  = nonNull(transactionRepository.sumByTypeAndDateRange(
            userId, TransactionType.DEBIT,  from, today));

        BigDecimal monthlyIncome = totalIncome.divide(
            BigDecimal.valueOf(LOOKBACK_MONTHS), 2, RoundingMode.HALF_UP);
        BigDecimal monthlySpend  = totalSpend.divide(
            BigDecimal.valueOf(LOOKBACK_MONTHS), 2, RoundingMode.HALF_UP);

        // Recurring commitments — sum of monthly equivalents from active patterns
        List<RecurringPattern> patterns = recurringPatternRepository
            .findByUserIdAndIsDismissedFalseOrderByEstimatedAmountDesc(userId);
        BigDecimal monthlyRecurring = patterns.stream()
            .filter(p -> p.getEstimatedAmount() != null)
            .map(p -> p.getEstimatedAmount()
                .multiply(BigDecimal.valueOf(p.getPeriod().getAnnualFrequency()))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyDiscretionary = monthlyIncome
            .subtract(monthlySpend)
            .subtract(monthlyRecurring.max(BigDecimal.ZERO))
            .max(BigDecimal.ZERO);

        BigDecimal monthlyNetSavings = monthlyIncome.subtract(monthlySpend);

        double savingsRate = monthlyIncome.compareTo(BigDecimal.ZERO) > 0
            ? monthlyNetSavings.doubleValue() / monthlyIncome.doubleValue()
            : 0.0;

        // Top category by spend
        List<Object[]> categoryRows = transactionRepository.categoryBreakdown(
            userId, TransactionType.DEBIT, from, today);
        String topCategoryName = null;
        BigDecimal topCategoryMonthly = BigDecimal.ZERO;
        if (!categoryRows.isEmpty()) {
            Object[] top = categoryRows.get(0); // already sorted DESC by sum
            TransactionCategory cat = (TransactionCategory) top[0];
            BigDecimal amount = top[1] instanceof BigDecimal b ? b : new BigDecimal(top[1].toString());
            topCategoryName    = cat.getDisplayName();
            topCategoryMonthly = amount.divide(BigDecimal.valueOf(LOOKBACK_MONTHS), 2, RoundingMode.HALF_UP);
        }

        boolean hasEnoughData = monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                             || monthlySpend.compareTo(BigDecimal.ZERO)  > 0;

        return FinancialBaseline.builder()
            .monthlyIncome(monthlyIncome)
            .monthlySpend(monthlySpend)
            .monthlyRecurring(monthlyRecurring)
            .monthlyDiscretionary(monthlyDiscretionary)
            .monthlyNetSavings(monthlyNetSavings)
            .savingsRate(Math.max(-1.0, Math.min(1.0, savingsRate)))
            .dataMonths(LOOKBACK_MONTHS)
            .topCategoryName(topCategoryName)
            .topCategoryMonthlySpend(topCategoryMonthly)
            .hasEnoughData(hasEnoughData)
            .build();
    }

    private static BigDecimal nonNull(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
