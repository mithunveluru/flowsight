package com.flowsight.analytics.simulation;

import com.flowsight.dto.simulation.FinancialBaseline;
import com.flowsight.dto.simulation.FlexibilityScore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Computes a 0–100 financial flexibility score from current and projected baselines.
 *
 * <p>The score combines three independent signals (each clamped to [0, 1]):
 * <ul>
 *   <li><b>A. Recurring health</b> (35%) — penalty when recurring commitments exceed
 *       50% of income. Captures "locked-in cash" risk.</li>
 *   <li><b>B. Savings rate</b> (35%) — full credit at 30% savings rate. Captures
 *       wealth-building velocity.</li>
 *   <li><b>C. Cash flow buffer</b> (30%) — uncommitted monthly cash as a share of
 *       income. Full credit at 20%. Captures absorption capacity for surprises.</li>
 * </ul>
 */
@Service
public class FlexibilityCalculator {

    private static final double W_RECURRING = 0.35;
    private static final double W_SAVINGS   = 0.35;
    private static final double W_BUFFER    = 0.30;

    public FlexibilityScore compute(FinancialBaseline current, FinancialBaseline projected) {
        int currentScore   = scoreFrom(current);
        int projectedScore = scoreFrom(projected);

        double delta = currentScore > 0
            ? ((double)(projectedScore - currentScore) / currentScore) * 100.0
            : 0.0;

        return FlexibilityScore.builder()
            .currentScore(currentScore)
            .projectedScore(projectedScore)
            .deltaPercent(Math.round(delta * 10.0) / 10.0)
            .currentTier(tierFor(currentScore))
            .projectedTier(tierFor(projectedScore))
            .explanation(buildExplanation(current, projected, currentScore, projectedScore))
            .build();
    }

    /** Computes a score for a single baseline — useful for non-scenario contexts. */
    public int scoreFrom(FinancialBaseline b) {
        if (!b.isHasEnoughData() || b.getMonthlyIncome().compareTo(BigDecimal.ZERO) <= 0) {
            // Without income data we can't compute a meaningful flexibility score.
            // Use a conservative neutral default.
            return 50;
        }

        double income     = b.getMonthlyIncome().doubleValue();
        double recurring  = Math.max(0, b.getMonthlyRecurring().doubleValue());
        double netSavings = b.getMonthlyNetSavings().doubleValue();
        double buffer     = b.getMonthlyDiscretionary().doubleValue();

        // Component A: recurring health — 1.0 when 0% locked in, 0.0 when 50%+ locked in
        double a = clamp(1.0 - (recurring / income) / 0.5);

        // Component B: savings rate — 1.0 at 30%+ savings rate
        double b1 = clamp((netSavings / income) / 0.3);

        // Component C: cash flow buffer — 1.0 at 20%+ uncommitted income
        double c = clamp((buffer / income) / 0.2);

        double total = a * W_RECURRING + b1 * W_SAVINGS + c * W_BUFFER;
        return (int) Math.round(total * 100);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static String tierFor(int score) {
        if (score >= 80) return "EXCELLENT";
        if (score >= 65) return "GOOD";
        if (score >= 50) return "FAIR";
        if (score >= 35) return "TIGHT";
        return "CONSTRAINED";
    }

    private String buildExplanation(
        FinancialBaseline current, FinancialBaseline projected,
        int currentScore, int projectedScore
    ) {
        int delta = projectedScore - currentScore;

        if (delta >= 3) {
            return "This decision improves your financial flexibility — more uncommitted cash, faster savings.";
        }
        if (delta <= -10) {
            return "This decision materially reduces your flexibility — recurring commitments climb and " +
                   "absorbing surprises gets harder.";
        }
        if (delta <= -3) {
            BigDecimal recurringDelta = projected.getMonthlyRecurring().subtract(current.getMonthlyRecurring());
            if (recurringDelta.compareTo(BigDecimal.ZERO) > 0) {
                return String.format(
                    "Adds ₹%,.0f/month to recurring commitments, narrowing your room to maneuver.",
                    recurringDelta.doubleValue());
            }
            return "Reduces discretionary spending capacity slightly.";
        }
        return "Marginal impact on overall financial flexibility.";
    }
}
