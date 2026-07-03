package com.flowsight.analytics;

import com.flowsight.dto.insights.BehavioralPattern;
import com.flowsight.dto.insights.Recommendation;
import com.flowsight.dto.leak.LeakDetectionResponse;
import com.flowsight.dto.leak.LeakInsight;
import com.flowsight.entity.FinancialGoal;
import com.flowsight.entity.GoalStatus;
import com.flowsight.repository.FinancialGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

// Fuses leaks, behaviors, and active goals into ranked recommendations (top 5 by monthly saving).
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationEngine {

    private static final int    MAX_RECOMMENDATIONS = 5;
    private static final BigDecimal NOISE_FLOOR     = new BigDecimal("100");

    private final LeakDetectionService     leakDetectionService;
    private final FinancialGoalRepository  goalRepository;

    public List<Recommendation> generate(UUID userId, List<BehavioralPattern> behaviors) {
        List<Recommendation> candidates = new ArrayList<>();

        // leaks -> cancel/reduce
        LeakDetectionResponse leaks = leakDetectionService.detectLeaks(userId);
        for (LeakInsight leak : leaks.getLeaks()) {
            Recommendation rec = fromLeak(leak);
            if (rec != null) candidates.add(rec);
        }

        // behaviors -> habit shifts
        for (BehavioralPattern pattern : behaviors) {
            Recommendation rec = fromBehavior(pattern);
            if (rec != null) candidates.add(rec);
        }

        // active goal + leaks -> suggest redirecting recovered savings
        List<FinancialGoal> activeGoals = goalRepository
            .findByUserIdAndStatusOrderByTargetDateAsc(userId, GoalStatus.ACTIVE);
        if (!activeGoals.isEmpty() && leaks.getTotalMonthlyImpact().compareTo(NOISE_FLOOR) > 0) {
            FinancialGoal nearestGoal = activeGoals.get(0);
            candidates.add(buildRedirectRecommendation(nearestGoal, leaks.getTotalMonthlyImpact()));
        }

        // rank by saving, cap
        return candidates.stream()
            .filter(r -> r.getPotentialMonthlySaving() != null
                      && r.getPotentialMonthlySaving().compareTo(NOISE_FLOOR) >= 0)
            .sorted(Comparator.comparing(Recommendation::getPotentialMonthlySaving).reversed())
            .limit(MAX_RECOMMENDATIONS)
            .collect(Collectors.toList());
    }

    private Recommendation fromLeak(LeakInsight leak) {
        if (leak.getMonthlyImpact() == null
         || leak.getMonthlyImpact().compareTo(NOISE_FLOOR) < 0) return null;

        return switch (leak.getType()) {
            case "DUPLICATE_SUBSCRIPTIONS" -> Recommendation.builder()
                .type("CANCEL_SUBSCRIPTION")
                .title("Consolidate overlapping subscriptions")
                .description(leak.getDescription())
                .suggestedAction(leak.getRecommendation())
                .potentialMonthlySaving(leak.getMonthlyImpact())
                .potentialAnnualSaving(leak.getAnnualImpact())
                .confidence(mapSeverity(leak.getSeverity()))
                .evidence(buildEvidence(leak))
                .build();
            case "SUBSCRIPTION_CREEP" -> Recommendation.builder()
                .type("REVIEW_INFLATION")
                .title("Renegotiate or downgrade rising subscriptions")
                .description(leak.getDescription())
                .suggestedAction(leak.getRecommendation())
                .potentialMonthlySaving(leak.getMonthlyImpact())
                .potentialAnnualSaving(leak.getAnnualImpact())
                .confidence(mapSeverity(leak.getSeverity()))
                .evidence(buildEvidence(leak))
                .build();
            case "HIGH_FREQUENCY_SMALL_SPEND" -> Recommendation.builder()
                .type("SHIFT_HABIT")
                .title("Reduce a high-frequency habit")
                .description(leak.getDescription())
                .suggestedAction(leak.getRecommendation())
                // realistic target: 50% reduction
                .potentialMonthlySaving(half(leak.getMonthlyImpact()))
                .potentialAnnualSaving(half(leak.getAnnualImpact()))
                .confidence(mapSeverity(leak.getSeverity()))
                .evidence(buildEvidence(leak))
                .build();
            case "BANK_FEES" -> Recommendation.builder()
                .type("REDUCE_CATEGORY")
                .title("Negotiate or eliminate bank fees")
                .description(leak.getDescription())
                .suggestedAction(leak.getRecommendation())
                .potentialMonthlySaving(leak.getMonthlyImpact())
                .potentialAnnualSaving(leak.getAnnualImpact())
                .confidence(mapSeverity(leak.getSeverity()))
                .evidence(buildEvidence(leak))
                .build();
            default -> null;
        };
    }

    private Recommendation fromBehavior(BehavioralPattern p) {
        return switch (p.getCode()) {
            case "WEEKEND_OVERSPEND" -> Recommendation.builder()
                .type("SHIFT_HABIT")
                .title("Cap weekend spending")
                .description(p.getDescription())
                .suggestedAction("Set a weekend budget that brings daily spend closer to your weekday baseline.")
                .potentialMonthlySaving(null) // not quantified without precise weekly data
                .potentialAnnualSaving(null)
                .confidence(p.getSeverity())
                .evidence(List.of(p.getContext()))
                .build();
            case "LIFESTYLE_INFLATION" -> Recommendation.builder()
                .type("REVIEW_INFLATION")
                .title("Check lifestyle inflation")
                .description(p.getDescription())
                .suggestedAction("Review your largest categories — small upgrades often compound silently.")
                .potentialMonthlySaving(null)
                .potentialAnnualSaving(null)
                .confidence(p.getSeverity())
                .evidence(List.of(p.getContext()))
                .build();
            default -> null;
        };
    }

    private Recommendation buildRedirectRecommendation(FinancialGoal goal, BigDecimal recoverableMonthly) {
        BigDecimal annualRecoverable = recoverableMonthly.multiply(BigDecimal.valueOf(12));
        return Recommendation.builder()
            .type("REDIRECT_SAVINGS")
            .title("Redirect recovered savings to '" + goal.getName() + "'")
            .description(String.format(
                "If you act on the savings above, you could contribute ₹%,.0f/month toward your goal.",
                recoverableMonthly.doubleValue()))
            .suggestedAction("Set up a recurring transfer to your goal once you cancel or reduce the items above.")
            .potentialMonthlySaving(recoverableMonthly)
            .potentialAnnualSaving(annualRecoverable.setScale(2, RoundingMode.HALF_UP))
            .confidence("MEDIUM")
            .evidence(List.of(
                "Goal target: ₹" + String.format("%,.0f", goal.getTargetAmount().doubleValue()),
                "Goal date: " + goal.getTargetDate()
            ))
            .build();
    }

    private static String mapSeverity(String leakSeverity) {
        return switch (leakSeverity) {
            case "HIGH"   -> "HIGH";
            case "MEDIUM" -> "MEDIUM";
            default        -> "LOW";
        };
    }

    private static BigDecimal half(BigDecimal v) {
        return v == null ? null
            : v.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private static List<String> buildEvidence(LeakInsight leak) {
        List<String> evidence = new ArrayList<>();
        evidence.add(leak.getAffectedItemsCount() + " items affected");
        evidence.add(String.format("₹%,.0f/year at current pace", leak.getAnnualImpact().doubleValue()));
        return evidence;
    }
}
