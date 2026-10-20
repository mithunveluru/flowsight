package com.flowsight.analytics;

import com.flowsight.dto.insights.*;
import com.flowsight.llm.FactSet;
import com.flowsight.llm.GroundedNarrator;
import com.flowsight.llm.PersonalDataRedactor;
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

    private static final String NARRATIVE_TASK =
        "Write one or two sentences (under 60 words) telling the person what stands out and what they could save.";
    private static final int NARRATIVE_MAX_CHARS = 500;

    private final BehavioralAnalysisService behavioralAnalysis;
    private final RecommendationEngine      recommendationEngine;
    private final ConsequenceCalculator     consequenceCalculator;
    private final GroundedNarrator          narrator;

    public InsightsResponse getInsights(UUID userId) {
        BehavioralProfile profile = behavioralAnalysis.analyze(userId);
        List<Recommendation> recommendations = recommendationEngine.generate(userId, profile.getPatterns());
        List<ConsequenceProjection> consequences = consequenceCalculator.topProjections(userId);

        // Only leak savings are additive. REDIRECT_SAVINGS re-spends them, and behavior estimates
        // overlap with the leaks and with each other.
        BigDecimal totalMonthly = recommendations.stream()
            .filter(r -> "LEAK".equals(r.getSource()))
            .map(Recommendation::getPotentialMonthlySaving)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalAnnual = totalMonthly.multiply(BigDecimal.valueOf(12))
            .setScale(2, RoundingMode.HALF_UP);

        return InsightsResponse.builder()
            .profile(profile)
            .recommendations(recommendations)
            .topConsequences(consequences)
            .totalPotentialMonthlySaving(totalMonthly)
            .totalPotentialAnnualSaving(totalAnnual)
            .narrative(narrative(profile, recommendations, totalMonthly, totalAnnual))
            .build();
    }

    private InsightNarrative narrative(BehavioralProfile profile, List<Recommendation> recs,
                                       BigDecimal monthly, BigDecimal annual) {
        FactSet facts = new FactSet()
            .text("profile", profile.getSummary())
            .count("suggestion_count", recs.size())
            .amount("leak_saving_monthly", monthly)
            .amount("leak_saving_annual", annual);
        String topTitle = profile.getPatterns() == null || profile.getPatterns().isEmpty()
            ? null : profile.getPatterns().get(0).getTitle();
        facts.text("top_pattern", topTitle);

        List<String> sentences = new java.util.ArrayList<>();
        if (profile.getSummary() != null) sentences.add(profile.getSummary() + ".");
        if (topTitle != null) sentences.add("What stands out: " + topTitle.toLowerCase() + ".");
        if (monthly.signum() > 0) {
            sentences.add(String.format("Acting on the leaks found could save ₹%,.0f a month (₹%,.0f a year).",
                monthly.doubleValue(), annual.doubleValue()));
        }
        GroundedNarrator.Narrative n = narrator.narrate(NARRATIVE_TASK, facts, String.join(" ", sentences),
            PersonalDataRedactor.forIdentifiers(List.of()), NARRATIVE_MAX_CHARS);
        return InsightNarrative.builder().text(n.text()).source(n.source().name()).build();
    }
}
