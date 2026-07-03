package com.flowsight.reports;

import com.flowsight.analytics.AnalyticsService;
import com.flowsight.analytics.BehavioralAnalysisService;
import com.flowsight.analytics.ConsequenceCalculator;
import com.flowsight.analytics.LeakDetectionService;
import com.flowsight.analytics.RecommendationEngine;
import com.flowsight.analytics.RecurringDetectionService;
import com.flowsight.dto.analytics.AnalyticsOverviewResponse;
import com.flowsight.dto.insights.BehavioralProfile;
import com.flowsight.dto.insights.ConsequenceProjection;
import com.flowsight.dto.insights.Recommendation;
import com.flowsight.dto.leak.LeakDetectionResponse;
import com.flowsight.dto.recurring.RecurringPatternResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

// Gathers every data point a report needs from the analytics services. Pure composition.
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportAnalyticsAggregator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final AnalyticsService           analyticsService;
    private final BehavioralAnalysisService  behavioralAnalysisService;
    private final RecommendationEngine       recommendationEngine;
    private final ConsequenceCalculator      consequenceCalculator;
    private final LeakDetectionService       leakDetectionService;
    private final RecurringDetectionService  recurringDetectionService;

    public ReportData aggregate(UUID userId, LocalDate from, LocalDate to) {
        AnalyticsOverviewResponse current = analyticsService.getOverview(userId, from, to);

        // same-length prior window for delta narration
        long windowDays = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate priorEnd   = from.minusDays(1);
        LocalDate priorStart = priorEnd.minusDays(windowDays - 1);
        AnalyticsOverviewResponse prior = analyticsService.getOverview(userId, priorStart, priorEnd);

        BigDecimal spendChange = current.getTotalSpend().subtract(prior.getTotalSpend());
        double spendChangePct = prior.getTotalSpend().compareTo(BigDecimal.ZERO) > 0
            ? spendChange.doubleValue() / prior.getTotalSpend().doubleValue() * 100.0
            : 0.0;

        // stored recurring (no re-scan; keeps the report deterministic)
        List<RecurringPatternResponse> patterns = recurringDetectionService.getStored(userId);
        BigDecimal monthlyRecurring = patterns.stream()
            .map(RecurringPatternResponse::getMonthlyEquivalent)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal annualRecurring = monthlyRecurring
            .multiply(BigDecimal.valueOf(12))
            .setScale(2, RoundingMode.HALF_UP);

        LeakDetectionResponse leaks = leakDetectionService.detectLeaks(userId);

        BehavioralProfile profile = behavioralAnalysisService.analyze(userId);
        List<Recommendation> recommendations = recommendationEngine.generate(userId, profile.getPatterns());
        List<ConsequenceProjection> topConsequences = consequenceCalculator.topProjections(userId);

        return ReportData.builder()
            .periodStart(from)
            .periodEnd(to)
            .periodLabel(from.format(DATE_FMT) + " – " + to.format(DATE_FMT))
            .currentPeriod(current)
            .priorPeriod(prior)
            .recurringPatterns(patterns)
            .monthlyRecurringTotal(monthlyRecurring)
            .annualRecurringTotal(annualRecurring)
            .leaks(leaks)
            .behavioralProfile(profile)
            .recommendations(recommendations)
            .topConsequences(topConsequences)
            .spendChange(spendChange)
            .spendChangePercent(Math.round(spendChangePct * 10.0) / 10.0)
            .build();
    }
}
