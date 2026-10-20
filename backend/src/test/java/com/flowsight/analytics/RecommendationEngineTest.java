package com.flowsight.analytics;

import com.flowsight.dto.insights.BehavioralPattern;
import com.flowsight.dto.insights.BehavioralProfile;
import com.flowsight.dto.insights.InsightsResponse;
import com.flowsight.dto.insights.Recommendation;
import com.flowsight.dto.leak.LeakDetectionResponse;
import com.flowsight.dto.leak.LeakInsight;
import com.flowsight.entity.FinancialGoal;
import com.flowsight.entity.GoalStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.insights.RecommendationRanker;
import com.flowsight.insights.RecommendationStatsRepository;
import com.flowsight.llm.ChatProvider;
import com.flowsight.llm.GroundedNarrator;
import com.flowsight.ml.PersonalizationService;
import com.flowsight.ml.UserFeedbackRepository;
import com.flowsight.repository.FinancialGoalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationEngineTest {

    private final LeakDetectionService leakDetection = mock(LeakDetectionService.class);
    private final FinancialGoalRepository goalRepository = mock(FinancialGoalRepository.class);
    private final UserFeedbackRepository feedback = mock(UserFeedbackRepository.class);
    private final RecommendationStatsRepository stats = mock(RecommendationStatsRepository.class);
    private final RecommendationRanker ranker =
        new RecommendationRanker(feedback, mock(PersonalizationService.class), stats);
    private final RecommendationEngine engine = new RecommendationEngine(leakDetection, goalRepository, ranker);

    private final List<BehavioralPattern> behaviors = List.of(
        behavior("WEEKEND_OVERSPEND", "You spend 85% more per day on weekends than weekdays."),
        behavior("LIFESTYLE_INFLATION", "Your spending has grown 22% over the last 3 months vs the prior 3."));

    @BeforeEach
    void setUp() {
        // silent drain 3,000/month (habit target: half) and bank fees 400/month
        when(leakDetection.detectLeaks(any())).thenReturn(LeakDetectionResponse.builder()
            .totalLeaksFound(2)
            .totalMonthlyImpact(new BigDecimal("3400.00"))
            .totalAnnualImpact(new BigDecimal("40800.00"))
            .leaks(List.of(
                leak("HIGH_FREQUENCY_SMALL_SPEND", "HIGH", "3000.00"),
                leak("BANK_FEES", "LOW", "400.00")))
            .build());
        when(goalRepository.findByUserIdAndStatusOrderByTargetDateAsc(any(), eq(GoalStatus.ACTIVE))).thenReturn(List.of(
            FinancialGoal.builder().name("Emergency fund").targetAmount(new BigDecimal("100000"))
                .targetDate(LocalDate.now().plusMonths(12)).build()));
    }

    @Test
    void behaviorRecommendationsAreReturnedWithoutASavingEstimate() {
        List<Recommendation> recs = engine.generate(UUID.randomUUID(), behaviors);

        assertThat(recs).extracting(Recommendation::getType)
            .containsExactly("REDIRECT_SAVINGS", "SHIFT_HABIT", "REDUCE_CATEGORY", "SHIFT_HABIT", "REVIEW_INFLATION");
        assertThat(recs.get(3).getTitle()).isEqualTo("Cap weekend spending");
        assertThat(recs.get(3).getPotentialMonthlySaving()).isNull();
        assertThat(recs.get(4).getPotentialMonthlySaving()).isNull();
    }

    @Test
    void redirectAmountIsWhatTheLeakRecommendationsRecover() {
        List<Recommendation> recs = engine.generate(UUID.randomUUID(), List.of());

        assertThat(recs.get(0).getType()).isEqualTo("REDIRECT_SAVINGS");
        // 1,500 (half of the 3,000 habit) + 400 fees, not the raw 3,400 leak total
        assertThat(recs.get(0).getPotentialMonthlySaving()).isEqualByComparingTo("1900");
    }

    @Test
    void quantifiedSavingsBelowNoiseFloorStayFiltered() {
        when(leakDetection.detectLeaks(any())).thenReturn(LeakDetectionResponse.builder()
            .totalMonthlyImpact(new BigDecimal("60.00"))
            .leaks(List.of(leak("BANK_FEES", "LOW", "60.00")))
            .build());

        List<Recommendation> recs = engine.generate(UUID.randomUUID(), List.of());

        assertThat(recs).isEmpty();
    }

    @Test
    void insightsHeadlineDoesNotCountRedirectedSavingsTwice() {
        BehavioralAnalysisService behavioral = mock(BehavioralAnalysisService.class);
        when(behavioral.analyze(any())).thenReturn(BehavioralProfile.builder().patterns(behaviors).build());
        ConsequenceCalculator consequences = mock(ConsequenceCalculator.class);

        ChatProvider noModel = mock(ChatProvider.class);
        GroundedNarrator narrator = new GroundedNarrator(noModel, new ObjectMapper());

        InsightsResponse insights = new InsightsService(behavioral, engine, consequences, narrator)
            .getInsights(UUID.randomUUID());

        assertThat(insights.getTotalPotentialMonthlySaving()).isEqualByComparingTo("1900");
        assertThat(insights.getTotalPotentialAnnualSaving()).isEqualByComparingTo("22800");
        assertThat(insights.getNarrative().getSource()).isEqualTo("TEMPLATE");
        assertThat(insights.getNarrative().getText()).contains("₹1,900 a month");
    }

    @Test
    void behaviorAdviceCarriesItsEstimateButStaysOutOfTheHeadline() {
        BehavioralPattern weekend = behavior("WEEKEND_OVERSPEND", "You spend 140% more per day on weekends than weekdays.");
        weekend.setMonthlySavingEstimate(new BigDecimal("4983.33"));
        BehavioralPattern tiny = behavior("LIFESTYLE_INFLATION", "Your spending has grown 12% over the last 3 months vs the prior 3.");
        tiny.setMonthlySavingEstimate(new BigDecimal("60"));
        BehavioralAnalysisService behavioral = mock(BehavioralAnalysisService.class);
        when(behavioral.analyze(any())).thenReturn(BehavioralProfile.builder().patterns(List.of(weekend, tiny)).build());

        InsightsResponse insights = new InsightsService(behavioral, engine, mock(ConsequenceCalculator.class),
            new GroundedNarrator(mock(ChatProvider.class), new ObjectMapper())).getInsights(UUID.randomUUID());

        Recommendation cap = insights.getRecommendations().stream()
            .filter(r -> "BEHAVIOR_WEEKEND_OVERSPEND".equals(r.getKind())).findFirst().orElseThrow();
        assertThat(cap.getPotentialMonthlySaving()).isEqualByComparingTo("4983.33");
        assertThat(cap.getPotentialAnnualSaving()).isEqualByComparingTo("59799.96");
        assertThat(cap.getSource()).isEqualTo("BEHAVIOR");
        assertThat(insights.getRecommendations()).filteredOn(r -> "BEHAVIOR_LIFESTYLE_INFLATION".equals(r.getKind()))
            .singleElement().extracting(Recommendation::getPotentialMonthlySaving).isNull();
        // still only the leak savings: 1,500 + 400
        assertThat(insights.getTotalPotentialMonthlySaving()).isEqualByComparingTo("1900");
    }

    @Test
    void idsAreStablePerUserAndDifferBetweenUsers() {
        UUID a = UUID.randomUUID();
        List<String> first = engine.candidates(a, behaviors).stream().map(Recommendation::getId).toList();
        List<String> again = engine.candidates(a, behaviors).stream().map(Recommendation::getId).toList();
        List<String> other = engine.candidates(UUID.randomUUID(), behaviors).stream().map(Recommendation::getId).toList();

        assertThat(again).isEqualTo(first);
        assertThat(other).doesNotContainAnyElementsOf(first);
    }

    private static BehavioralPattern behavior(String code, String description) {
        return BehavioralPattern.builder().code(code).severity("MEDIUM").description(description)
            .context("from the last 6 months").build();
    }

    private static LeakInsight leak(String type, String severity, String monthly) {
        BigDecimal m = new BigDecimal(monthly);
        return LeakInsight.builder().type(type).severity(severity).description(type).recommendation(type)
            .monthlyImpact(m).annualImpact(m.multiply(BigDecimal.valueOf(12))).affectedItemsCount(1).build();
    }
}
