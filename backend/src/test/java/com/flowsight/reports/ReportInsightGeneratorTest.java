package com.flowsight.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.dto.analytics.AnalyticsOverviewResponse;
import com.flowsight.dto.analytics.CategoryBreakdownItem;
import com.flowsight.dto.insights.BehavioralProfile;
import com.flowsight.dto.leak.LeakDetectionResponse;
import com.flowsight.llm.GroundedNarrator;
import com.flowsight.llm.GroundingValidator;
import com.flowsight.llm.StubChatProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportInsightGeneratorTest {

    private final ReportData data = ReportData.builder()
        .periodStart(LocalDate.of(2026, 9, 1))
        .periodEnd(LocalDate.of(2026, 9, 30))
        .periodLabel("1 Sep 2026 – 30 Sep 2026")
        .currentPeriod(AnalyticsOverviewResponse.builder()
            .transactionCount(84)
            .totalSpend(new BigDecimal("52340.75"))
            .totalIncome(new BigDecimal("90000"))
            .netCashflow(new BigDecimal("37659.25"))
            .categoryBreakdown(List.of(CategoryBreakdownItem.builder().category("FOOD_DINING")
                .displayName("Food & Dining").amount(new BigDecimal("14200")).percentage(27.1).build()))
            .build())
        .priorPeriod(AnalyticsOverviewResponse.builder().totalSpend(new BigDecimal("46000")).build())
        .spendChange(new BigDecimal("6340.75"))
        .spendChangePercent(13.8)
        .behavioralProfile(BehavioralProfile.builder().summary("Weekend-driven spender").patterns(List.of()).build())
        .leaks(LeakDetectionResponse.builder().totalMonthlyImpact(new BigDecimal("1900"))
            .totalAnnualImpact(new BigDecimal("22800")).leaks(List.of()).build())
        .recurringPatterns(List.of())
        .build();

    private ReportInsightGenerator generator(StubChatProvider stub) {
        return new ReportInsightGenerator(new GroundedNarrator(stub, new ObjectMapper()));
    }

    @Test
    void templateSummaryIsGroundedInItsOwnFacts() {
        ReportInsightGenerator.ReportNarrative n = generator(new StubChatProvider(false)).generate(data);

        assertThat(n.getExecutiveSummarySource()).isEqualTo("TEMPLATE");
        assertThat(n.getExecutiveSummary()).hasSize(6);
        GroundingValidator.Result check = GroundingValidator.check(
            String.join(" ", n.getExecutiveSummary()), ReportInsightGenerator.summaryFacts(data));
        assertThat(check.grounded()).as(check.ungrounded().toString()).isTrue();
    }

    @Test
    void groundedModelSummaryIsSplitIntoSentences() {
        ReportInsightGenerator.ReportNarrative n = generator(new StubChatProvider(true).reply(
            "From 1 Sep 2026 to 30 Sep 2026 you spent ₹52,341 against ₹90,000 of income. "
            + "That is 13.8% more than the previous period. Food & Dining led at 27%."))
            .generate(data);

        assertThat(n.getExecutiveSummarySource()).isEqualTo("LLM");
        assertThat(n.getExecutiveSummary()).hasSize(3);
    }

    @Test
    void ungroundedModelSummaryIsReplacedByTheTemplate() {
        ReportInsightGenerator.ReportNarrative n = generator(new StubChatProvider(true).reply(
            "You spent ₹52,341, on track for ₹6.3 lakh this year.")).generate(data);

        assertThat(n.getExecutiveSummarySource()).isEqualTo(GroundedNarrator.Source.TEMPLATE.name());
        assertThat(n.getExecutiveSummary().get(0)).startsWith("Across 1 Sep 2026 – 30 Sep 2026 you recorded 84 transactions");
    }
}
