package com.flowsight.reports;

import com.flowsight.dto.analytics.CategoryBreakdownItem;
import com.flowsight.dto.insights.BehavioralPattern;
import com.flowsight.dto.insights.Recommendation;
import com.flowsight.dto.leak.LeakInsight;
import com.flowsight.dto.recurring.RecurringPatternResponse;
import com.flowsight.llm.FactSet;
import com.flowsight.llm.GroundedNarrator;
import com.flowsight.llm.PersonalDataRedactor;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Builds the report's narrative prose; every sentence is grounded in ReportData numbers.
// The executive summary may be rewritten by the LLM, which is held to the same facts.
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportInsightGenerator {

    private static final String SUMMARY_TASK =
        "Write the executive summary of a spending report in three to five sentences (under 120 words).";
    private static final int SUMMARY_MAX_CHARS = 1200;

    private final GroundedNarrator narrator;

    public ReportNarrative generate(ReportData data) {
        List<String> template = buildExecutiveSummary(data);
        GroundedNarrator.Narrative summary = narrator.narrate(SUMMARY_TASK, summaryFacts(data),
            String.join(" ", template), PersonalDataRedactor.forIdentifiers(List.of()), SUMMARY_MAX_CHARS);
        List<String> executive = summary.source() == GroundedNarrator.Source.LLM
            ? Arrays.asList(summary.text().split("(?<=[.!?])\\s+"))
            : template;
        return new ReportNarrative(
            executive,
            buildBehaviorAnalysis(data),
            buildLeakAnalysis(data),
            buildRecurringSummary(data),
            buildConsequenceParagraph(data),
            buildRecommendationLines(data),
            summary.source().name()
        );
    }

    // Everything the executive summary template states, and nothing more.
    static FactSet summaryFacts(ReportData d) {
        var current = d.getCurrentPeriod();
        FactSet facts = new FactSet()
            .date("period_start", d.getPeriodStart())
            .date("period_end", d.getPeriodEnd())
            .count("transaction_count", current.getTransactionCount())
            .amount("total_spend", current.getTotalSpend())
            .amount("total_income", current.getTotalIncome())
            .amount("net_cashflow", current.getNetCashflow());
        if (d.getPriorPeriod().getTotalSpend().compareTo(BigDecimal.ZERO) > 0) {
            facts.amount("prior_period_spend", d.getPriorPeriod().getTotalSpend())
                .amount("spend_change", d.getSpendChange())
                .percent("spend_change_percent", d.getSpendChangePercent());
        }
        if (!current.getCategoryBreakdown().isEmpty()) {
            var top = current.getCategoryBreakdown().get(0);
            facts.text("top_category", top.getDisplayName())
                .amount("top_category_spend", top.getAmount())
                .percent("top_category_share_percent", top.getPercentage());
        }
        if (d.getBehavioralProfile() != null) facts.text("spender_profile", d.getBehavioralProfile().getSummary());
        if (d.getLeaks() != null && d.getLeaks().getTotalMonthlyImpact() != null) {
            facts.amount("leak_saving_monthly", d.getLeaks().getTotalMonthlyImpact())
                .amount("leak_saving_annual", d.getLeaks().getTotalAnnualImpact());
        }
        return facts;
    }

    private List<String> buildExecutiveSummary(ReportData d) {
        List<String> lines = new ArrayList<>();
        var current = d.getCurrentPeriod();

        String opening;
        if (current.getTransactionCount() == 0) {
            opening = String.format(
                "Over %s, no transactions were recorded against your account.",
                d.getPeriodLabel());
        } else {
            opening = String.format(
                "Across %s you recorded %d transactions, totaling ₹%,.0f in spend against ₹%,.0f in income.",
                d.getPeriodLabel(),
                current.getTransactionCount(),
                current.getTotalSpend().doubleValue(),
                current.getTotalIncome().doubleValue());
        }
        lines.add(opening);

        if (d.getPriorPeriod().getTotalSpend().compareTo(BigDecimal.ZERO) > 0) {
            double pct = d.getSpendChangePercent();
            String direction = pct >= 0 ? "rose by" : "fell by";
            lines.add(String.format(
                "Compared to the previous equivalent window, your spend %s %.0f%% (a difference of ₹%,.0f).",
                direction, Math.abs(pct), Math.abs(d.getSpendChange().doubleValue())));
        }

        BigDecimal net = current.getNetCashflow();
        if (net.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(String.format(
                "Net cashflow was positive at ₹%,.0f — you accumulated rather than depleted savings.",
                net.doubleValue()));
        } else if (net.compareTo(BigDecimal.ZERO) < 0) {
            lines.add(String.format(
                "Net cashflow was negative by ₹%,.0f — outflows exceeded income for the period.",
                Math.abs(net.doubleValue())));
        }

        if (!current.getCategoryBreakdown().isEmpty()) {
            var top = current.getCategoryBreakdown().get(0);
            lines.add(String.format(
                "%s was your largest category at ₹%,.0f (%.0f%% of total spend).",
                top.getDisplayName(),
                top.getAmount().doubleValue(),
                top.getPercentage()));
        }

        String profileSummary = d.getBehavioralProfile().getSummary();
        if (profileSummary != null && !profileSummary.isBlank()
                && !"Steady spender".equalsIgnoreCase(profileSummary)) {
            lines.add("Behaviourally, you're best described as: " + profileSummary.toLowerCase() + ".");
        }

        if (d.getLeaks() != null && d.getLeaks().getTotalMonthlyImpact() != null
                && d.getLeaks().getTotalMonthlyImpact().compareTo(new BigDecimal("100")) > 0) {
            lines.add(String.format(
                "Across detected leaks, recoverable monthly spend is approximately ₹%,.0f (₹%,.0f/year).",
                d.getLeaks().getTotalMonthlyImpact().doubleValue(),
                d.getLeaks().getTotalAnnualImpact().doubleValue()));
        }

        return lines;
    }

    private List<String> buildBehaviorAnalysis(ReportData d) {
        List<String> lines = new ArrayList<>();

        for (BehavioralPattern pattern : d.getBehavioralProfile().getPatterns()) {
            lines.add(pattern.getDescription() + " " + pattern.getContext() + ".");
        }

        if (d.getPriorPeriod().getTotalSpend().compareTo(new BigDecimal("100")) > 0) {
            double pct = d.getSpendChangePercent();
            if (Math.abs(pct) >= 10) {
                String severity = Math.abs(pct) >= 25 ? "significantly" : "noticeably";
                String direction = pct >= 0 ? "higher" : "lower";
                lines.add(String.format(
                    "Overall spending was %s %s than the prior equivalent window — a %.0f%% change.",
                    severity, direction, Math.abs(pct)));
            }
        }

        var current = d.getCurrentPeriod();
        if (!current.getCategoryBreakdown().isEmpty()) {
            CategoryBreakdownItem top = current.getCategoryBreakdown().get(0);
            if (top.getPercentage() > 40) {
                lines.add(String.format(
                    "Your top category (%s) represents %.0f%% of total spend — diversification could reduce single-category risk.",
                    top.getDisplayName(), top.getPercentage()));
            }
        }

        if (lines.isEmpty()) {
            lines.add("No notable behavioural patterns were detected for this window — your spending was consistent and within expected ranges.");
        }
        return lines;
    }

    private List<LeakLine> buildLeakAnalysis(ReportData d) {
        List<LeakLine> lines = new ArrayList<>();
        if (d.getLeaks() == null) return lines;

        for (LeakInsight leak : d.getLeaks().getLeaks()) {
            lines.add(new LeakLine(
                leak.getTitle(),
                leak.getDescription(),
                leak.getRecommendation(),
                String.format("₹%,.0f/month", leak.getMonthlyImpact().doubleValue()),
                String.format("₹%,.0f/year",  leak.getAnnualImpact().doubleValue()),
                leak.getSeverity()
            ));
        }
        return lines;
    }

    private List<String> buildRecurringSummary(ReportData d) {
        List<String> lines = new ArrayList<>();
        if (d.getRecurringPatterns() == null || d.getRecurringPatterns().isEmpty()) {
            lines.add("No recurring obligations have been detected from your transaction history.");
            return lines;
        }

        BigDecimal monthly = d.getMonthlyRecurringTotal();
        BigDecimal annual  = d.getAnnualRecurringTotal();
        lines.add(String.format(
            "Your active recurring commitments total ₹%,.0f/month (₹%,.0f/year) across %d distinct subscriptions or bills.",
            monthly.doubleValue(), annual.doubleValue(), d.getRecurringPatterns().size()));

        RecurringPatternResponse top = d.getRecurringPatterns().get(0);
        if (top.getEstimatedAmount() != null) {
            lines.add(String.format(
                "The largest single obligation is %s at ₹%,.0f/%s.",
                top.getMerchant(),
                top.getEstimatedAmount().doubleValue(),
                top.getPeriodLabel().toLowerCase()));
        }

        long cancellable = d.getRecurringPatterns().stream()
            .filter(RecurringPatternResponse::isCancellationCandidate).count();
        if (cancellable > 0) {
            lines.add(String.format(
                "%d of these are flagged as cancellation candidates — entertainment, subscription, or education category.",
                cancellable));
        }

        return lines;
    }

    private List<String> buildConsequenceParagraph(ReportData d) {
        List<String> lines = new ArrayList<>();
        if (d.getTopConsequences() == null || d.getTopConsequences().isEmpty()) {
            return lines;
        }

        BigDecimal totalAnnual = d.getTopConsequences().stream()
            .map(c -> c.getMonthlyAmount().multiply(BigDecimal.valueOf(12)))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTenYrOpportunity = d.getTopConsequences().stream()
            .map(c -> c.getTenYearOpportunityCost() != null ? c.getTenYearOpportunityCost() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        lines.add(String.format(
            "Across your top recurring expenses, the annual outlay is ₹%,.0f. " +
            "Sustained over ten years and compared with investing the same amount at 8%% annual return, " +
            "the opportunity cost is approximately ₹%,.0f.",
            totalAnnual.doubleValue(), totalTenYrOpportunity.doubleValue()));

        var top = d.getTopConsequences().get(0);
        if (top.getTenYearOpportunityCost() != null) {
            lines.add(String.format(
                "For example, '%s' at ₹%,.0f/month compounds to ₹%,.0f over 10 years if that monthly amount were instead invested.",
                top.getLabel(),
                top.getMonthlyAmount().doubleValue(),
                top.getTenYearOpportunityCost().doubleValue()));
        }
        return lines;
    }

    private List<RecommendationLine> buildRecommendationLines(ReportData d) {
        List<RecommendationLine> lines = new ArrayList<>();
        if (d.getRecommendations() == null) return lines;

        for (Recommendation r : d.getRecommendations()) {
            String impactLabel = "";
            if (r.getPotentialMonthlySaving() != null
                    && r.getPotentialMonthlySaving().compareTo(BigDecimal.ZERO) > 0) {
                impactLabel = String.format("Save up to ₹%,.0f/month",
                    r.getPotentialMonthlySaving().doubleValue());
            }
            lines.add(new RecommendationLine(
                r.getTitle(),
                r.getDescription(),
                r.getSuggestedAction(),
                impactLabel
            ));
        }
        return lines;
    }

    @Value
    public static class ReportNarrative {
        List<String> executiveSummary;
        List<String> behaviorAnalysis;
        List<LeakLine> leakLines;
        List<String> recurringSummary;
        List<String> consequenceParagraph;
        List<RecommendationLine> recommendations;
        String executiveSummarySource;      // LLM | TEMPLATE
    }

    @Value
    public static class LeakLine {
        String title;
        String description;
        String recommendation;
        String monthlyImpact;
        String annualImpact;
        String severity;
    }

    @Value
    public static class RecommendationLine {
        String title;
        String description;
        String action;
        String impactLabel;
    }
}
