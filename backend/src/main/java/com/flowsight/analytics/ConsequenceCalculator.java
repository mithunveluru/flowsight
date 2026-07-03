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

// Long-term cost of recurring expenses via FV-of-annuity at an assumed 8% return.
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsequenceCalculator {

    private static final double ASSUMED_ANNUAL_RETURN = 0.08;
    private static final int    PROJECTION_COUNT      = 5;   // top 5 by monthly impact

    private final RecurringPatternRepository patternRepository;

    // top recurring expenses by monthly impact; non-dismissed, amount > 0
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

    // projection for an arbitrary monthly amount (UI what-ifs)
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

    private ConsequenceProjection projectFromPattern(RecurringPattern p) {
        // normalize to a monthly equivalent
        BigDecimal monthly = p.getEstimatedAmount()
            .multiply(BigDecimal.valueOf(p.getPeriod().getAnnualFrequency()))
            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        return project(p.getMerchant(), monthly, p.getPeriod().getDisplayName());
    }

    private BigDecimal multiplyMonths(BigDecimal monthly, int months) {
        return monthly.multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.HALF_UP);
    }

    // FV of a monthly annuity, compounded monthly
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
