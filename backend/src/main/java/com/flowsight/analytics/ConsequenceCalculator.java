package com.flowsight.analytics;

import com.flowsight.dto.insights.ConsequenceProjection;
import com.flowsight.entity.RecurringPattern;
import com.flowsight.repository.RecurringPatternRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes the long-term cost of recurring expenses — FlowSight's "decision consequence".
 *
 * <p>The opportunity cost calculation uses the future-value-of-annuity formula:
 * <pre>
 *   FV = P × ((1+r)^n − 1) / r
 * </pre>
 * where:
 * <ul>
 *   <li>P = monthly payment amount</li>
 *   <li>r = monthly rate = annualRate / 12</li>
 *   <li>n = number of months (120 for 10 years)</li>
 * </ul>
 *
 * <p>The assumed annual return ({@link #ASSUMED_ANNUAL_RETURN}) is 8% — a conservative
 * long-run equity index assumption. The projection answers: "If you cancelled this and
 * invested the same amount each month instead, what would you have in 10 years?"
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsequenceCalculator {

    private static final double ASSUMED_ANNUAL_RETURN = 0.08;
    private static final int    PROJECTION_COUNT      = 5;   // top 5 by monthly impact

    private final RecurringPatternRepository patternRepository;

    /**
     * Returns projections for the user's top recurring expenses, sorted by monthly impact.
     * Only includes non-dismissed patterns with an estimated amount.
     */
    public List<ConsequenceProjection> topProjections(UUID userId) {
        List<RecurringPattern> patterns = patternRepository
            .findByUserIdAndIsDismissedFalseOrderByEstimatedAmountDesc(userId);

        return patterns.stream()
            .filter(p -> p.getEstimatedAmount() != null
                      && p.getEstimatedAmount().compareTo(BigDecimal.ZERO) > 0)
            .map(this::projectFromPattern)
            .sorted(Comparator.comparing(ConsequenceProjection::getMonthlyAmount).reversed())
            .limit(PROJECTION_COUNT)
            .collect(Collectors.toList());
    }

    /**
     * Computes a projection for an arbitrary recurring monthly amount.
     * Useful for the UI to model hypothetical changes.
     */
    public ConsequenceProjection project(String label, BigDecimal monthlyAmount, String category) {
        return ConsequenceProjection.builder()
            .label(label)
            .category(category)
            .monthlyAmount(monthlyAmount.setScale(2, RoundingMode.HALF_UP))
            .yearCost(multiplyMonths(monthlyAmount, 12))
            .fiveYearCost(multiplyMonths(monthlyAmount, 60))
            .tenYearCost(multiplyMonths(monthlyAmount, 120))
            .tenYearOpportunityCost(futureValueOfAnnuity(monthlyAmount, ASSUMED_ANNUAL_RETURN, 120))
            .reflection(buildReflection(monthlyAmount))
            .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ConsequenceProjection projectFromPattern(RecurringPattern p) {
        // Convert to monthly equivalent based on period
        BigDecimal monthly = p.getEstimatedAmount()
            .multiply(BigDecimal.valueOf(p.getPeriod().getAnnualFrequency()))
            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        return project(p.getMerchant(), monthly, p.getPeriod().getDisplayName());
    }

    private BigDecimal multiplyMonths(BigDecimal monthly, int months) {
        return monthly.multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Computes FV of a monthly annuity at the assumed annual return.
     * Returns the rupee value if you invested {@code monthly} every month for {@code months} months
     * at {@code annualRate} compounded monthly.
     */
    private BigDecimal futureValueOfAnnuity(BigDecimal monthly, double annualRate, int months) {
        double r = annualRate / 12.0;
        double fv = monthly.doubleValue() * (Math.pow(1 + r, months) - 1) / r;
        return BigDecimal.valueOf(fv).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildReflection(BigDecimal monthlyAmount) {
        double monthly = monthlyAmount.doubleValue();
        if      (monthly < 100)   return "A small amount that compounds quietly.";
        else if (monthly < 500)   return "Small now, meaningful over a decade.";
        else if (monthly < 2000)  return "A choice worth revisiting.";
        else                       return "A large compounding commitment.";
    }
}
