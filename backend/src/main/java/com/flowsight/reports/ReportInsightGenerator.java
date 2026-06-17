package com.flowsight.reports;

import com.flowsight.dto.analytics.CategoryBreakdownItem;
import com.flowsight.dto.insights.BehavioralPattern;
import com.flowsight.dto.insights.Recommendation;
import com.flowsight.dto.leak.LeakInsight;
import com.flowsight.dto.recurring.RecurringPatternResponse;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the narrative sections of the report.
 *
 * <p>Every sentence is data-grounded — no generic AI-generated platitudes.
 * Numbers always come from {@link ReportData}; this class only generates
 * the prose that explains them.
 *
 * <p>Future enhancement: a Groq-powered polish pass that rewrites each
 * paragraph in a chosen tone. The current deterministic templates are
 * intentionally written to read naturally without needing AI rewriting.
 */
@Service
@Slf4j
public class ReportInsightGenerator {

    public ReportNarrative generate(ReportData data) {
        return new ReportNarrative(
            buildExecutiveSummary(data),
            buildBehaviorAnalysis(data),
            buildLeakAnalysis(data),
            buildRecurringSummary(data),
            buildConsequenceParagraph(data),
            buildRecommendationLines(data)
        );
    }

    // 1. Executive summary — front page

    private List<String> buildExecutiveSummary(ReportData d) {
        List<String> lines = new ArrayList<>();
        var current = d.getCurrentPeriod();

        // Opening line — sets the period
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

        // Period-over-period delta
        if (d.getPriorPeriod().getTotalSpend().compareTo(BigDecimal.ZERO) > 0) {
            double pct = d.getSpendChangePercent();
            String direction = pct >= 0 ? "rose by" : "fell by";
            lines.add(String.format(
                "Compared to the previous equivalent window, your spend %s %.0f%% (a difference of ₹%,.0f).",
                direction, Math.abs(pct), Math.abs(d.getSpendChange().doubleValue())));
        }

        // Net cashflow tone
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

        // Top categories
        if (!current.getCategoryBreakdown().isEmpty()) {
            var top = current.getCategoryBreakdown().get(0);
            lines.add(String.format(
                "%s was your largest category at ₹%,.0f (%.0f%% of total spend).",
                top.getDisplayName(),
                top.getAmount().doubleValue(),
                top.getPercentage()));
        }

        // Behavioural one-liner
        String profileSummary = d.getBehavioralProfile().getSummary();
        if (profileSummary != null && !profileSummary.isBlank()
                && !"Steady spender".equalsIgnoreCase(profileSummary)) {
            lines.add("Behaviourally, you're best described as: " + profileSummary.toLowerCase() + ".");
        }

        // Leak headline
        if (d.getLeaks() != null && d.getLeaks().getTotalMonthlyImpact() != null
                && d.getLeaks().getTotalMonthlyImpact().compareTo(new BigDecimal("100")) > 0) {
            lines.add(String.format(
                "Across detected leaks, recoverable monthly spend is approximately ₹%,.0f (₹%,.0f/year).",
                d.getLeaks().getTotalMonthlyImpact().doubleValue(),
                d.getLeaks().getTotalAnnualImpact().doubleValue()));
        }

        return lines;
    }

    // 2. Spending behavior analysis

    private List<String> buildBehaviorAnalysis(ReportData d) {
        List<String> lines = new ArrayList<>();

        // Behavioral patterns from Phase 10
        for (BehavioralPattern pattern : d.getBehavioralProfile().getPatterns()) {
            lines.add(pattern.getDescription() + " " + pattern.getContext() + ".");
        }

        // Period delta narrative when there's enough spend to compare
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

        // Category concentration check
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

    // 3. Leak analysis

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

    // 4. Recurring summary

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

        // Top recurring obligation
        RecurringPatternResponse top = d.getRecurringPatterns().get(0);
        if (top.getEstimatedAmount() != null) {
            lines.add(String.format(
                "The largest single obligation is %s at ₹%,.0f/%s.",
                top.getMerchant(),
                top.getEstimatedAmount().doubleValue(),
                top.getPeriodLabel().toLowerCase()));
        }

        // Cancellation candidates
        long cancellable = d.getRecurringPatterns().stream()
            .filter(RecurringPatternResponse::isCancellationCandidate).count();
        if (cancellable > 0) {
            lines.add(String.format(
                "%d of these are flagged as cancellation candidates — entertainment, subscription, or education category.",
                cancellable));
        }

        return lines;
    }

    // 5. Consequence paragraph

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

        // Highlight one specific commitment
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

    // 6. Recommendation lines

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

    // Output structures

    @Value
    public static class ReportNarrative {
        List<String> executiveSummary;
        List<String> behaviorAnalysis;
        List<LeakLine> leakLines;
        List<String> recurringSummary;
        List<String> consequenceParagraph;
        List<RecommendationLine> recommendations;
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
