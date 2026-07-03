package com.flowsight.analytics;

import com.flowsight.dto.insights.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

// Composes behavioral profile, recommendations, and consequence projections. On demand.
@Service
@RequiredArgsConstructor
@Slf4j
public class InsightsService {

    private final BehavioralAnalysisService behavioralAnalysis;
    private final RecommendationEngine      recommendationEngine;
    private final ConsequenceCalculator     consequenceCalculator;

    public InsightsResponse getInsights(UUID userId) {
        BehavioralProfile profile = behavioralAnalysis.analyze(userId);
        List<Recommendation> recommendations = recommendationEngine.generate(userId, profile.getPatterns());
        List<ConsequenceProjection> consequences = consequenceCalculator.topProjections(userId);

        BigDecimal totalMonthly = recommendations.stream()
            .map(Recommendation::getPotentialMonthlySaving)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAnnual = totalMonthly.multiply(BigDecimal.valueOf(12))
            .setScale(2, RoundingMode.HALF_UP);

        return InsightsResponse.builder()
            .profile(profile)
            .recommendations(recommendations)
            .topConsequences(consequences)
            .totalPotentialMonthlySaving(totalMonthly.setScale(2, RoundingMode.HALF_UP))
            .totalPotentialAnnualSaving(totalAnnual)
            .build();
    }
}
