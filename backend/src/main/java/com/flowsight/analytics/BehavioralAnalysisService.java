package com.flowsight.analytics;

import com.flowsight.dto.insights.BehavioralPattern;
import com.flowsight.dto.insights.BehavioralProfile;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import com.flowsight.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects behavioral spending patterns from transaction history.
 *
 * <p>All analysis uses 6 months of DEBIT transactions. Patterns surfaced:
 * <ul>
 *   <li><b>WEEKEND_OVERSPEND</b> — weekend daily spend > weekday daily spend × 1.4</li>
 *   <li><b>LIFESTYLE_INFLATION</b> — recent 3-month avg > previous 3-month avg by ≥15%</li>
 *   <li><b>CATEGORY_CONCENTRATION</b> — top category > 40% of total spend</li>
 *   <li><b>INCREASING_FREQUENCY</b> — transactions/week growing month over month</li>
 *   <li><b>LARGE_TICKET_TREND</b> — avg transaction size growing 20%+ over the period</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BehavioralAnalysisService {

    private static final int LOOKBACK_MONTHS = 6;

    // Pattern thresholds
    private static final double WEEKEND_OVERSPEND_RATIO   = 1.40;
    private static final double LIFESTYLE_INFLATION_RATIO = 1.15;
    private static final double CATEGORY_DOMINANCE_PCT    = 40.0;
    private static final double FREQUENCY_GROWTH_RATIO    = 1.20;
    private static final double TICKET_GROWTH_RATIO       = 1.20;

    private final TransactionRepository transactionRepository;

    public BehavioralProfile analyze(UUID userId) {
        LocalDate from = LocalDate.now().minusMonths(LOOKBACK_MONTHS);
        List<Transaction> txns = transactionRepository.findForExport(userId, null, from, LocalDate.now())
            .stream()
            .filter(t -> t.getType() == TransactionType.DEBIT)
            .collect(Collectors.toList());

        List<BehavioralPattern> patterns = new ArrayList<>();
        addIfPresent(patterns, detectWeekendOverspend(txns));
        addIfPresent(patterns, detectLifestyleInflation(txns));
        addIfPresent(patterns, detectCategoryConcentration(txns));
        addIfPresent(patterns, detectIncreasingFrequency(txns));
        addIfPresent(patterns, detectLargeTicketTrend(txns));

        // Sort patterns by severity (HIGH first)
        patterns.sort(Comparator.comparingInt(BehavioralAnalysisService::severityOrder));

        String summary = buildSummary(patterns);

        return BehavioralProfile.builder()
            .summary(summary)
            .patterns(patterns)
            .build();
    }

    // 1) Weekend overspend

    private BehavioralPattern detectWeekendOverspend(List<Transaction> txns) {
        BigDecimal weekendTotal = BigDecimal.ZERO;
        BigDecimal weekdayTotal = BigDecimal.ZERO;
        int weekendDays = 0, weekdayDays = 0;
        Set<LocalDate> seenWeekend = new HashSet<>(), seenWeekday = new HashSet<>();

        for (Transaction tx : txns) {
            DayOfWeek dow = tx.getTransactionDate().getDayOfWeek();
            boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            if (weekend) {
                weekendTotal = weekendTotal.add(tx.getAmount());
                if (seenWeekend.add(tx.getTransactionDate())) weekendDays++;
            } else {
                weekdayTotal = weekdayTotal.add(tx.getAmount());
                if (seenWeekday.add(tx.getTransactionDate())) weekdayDays++;
            }
        }

        if (weekendDays == 0 || weekdayDays == 0) return null;

        double weekendDaily = weekendTotal.doubleValue() / weekendDays;
        double weekdayDaily = weekdayTotal.doubleValue() / weekdayDays;
        if (weekdayDaily == 0)                          return null;

        double ratio = weekendDaily / weekdayDaily;
        if (ratio < WEEKEND_OVERSPEND_RATIO)             return null;

        String severity = ratio >= 2.0 ? "HIGH" : ratio >= 1.7 ? "MEDIUM" : "LOW";
        double overspendPct = (ratio - 1) * 100;

        return BehavioralPattern.builder()
            .code("WEEKEND_OVERSPEND")
            .title("Weekend-driven spending")
            .description(String.format(
                "You spend %.0f%% more per day on weekends than weekdays.", overspendPct))
            .severity(severity)
            .value(BigDecimal.valueOf(overspendPct).setScale(1, RoundingMode.HALF_UP))
            .unit("%")
            .context(String.format("₹%.0f/day weekend vs ₹%.0f/day weekday", weekendDaily, weekdayDaily))
            .build();
    }

    // 2) Lifestyle inflation

    private BehavioralPattern detectLifestyleInflation(List<Transaction> txns) {
        LocalDate today = LocalDate.now();
        LocalDate midpoint = today.minusMonths(3);

        BigDecimal recent = BigDecimal.ZERO, earlier = BigDecimal.ZERO;
        for (Transaction tx : txns) {
            if (tx.getTransactionDate().isAfter(midpoint)) {
                recent = recent.add(tx.getAmount());
            } else {
                earlier = earlier.add(tx.getAmount());
            }
        }

        if (earlier.compareTo(BigDecimal.ZERO) == 0) return null;

        double ratio = recent.doubleValue() / earlier.doubleValue();
        if (ratio < LIFESTYLE_INFLATION_RATIO)         return null;

        String severity = ratio >= 1.40 ? "HIGH" : ratio >= 1.25 ? "MEDIUM" : "LOW";
        double inflationPct = (ratio - 1) * 100;

        return BehavioralPattern.builder()
            .code("LIFESTYLE_INFLATION")
            .title("Lifestyle inflation detected")
            .description(String.format(
                "Your spending has grown %.0f%% over the last 3 months vs the prior 3.",
                inflationPct))
            .severity(severity)
            .value(BigDecimal.valueOf(inflationPct).setScale(1, RoundingMode.HALF_UP))
            .unit("%")
            .context(String.format("₹%,.0f recent vs ₹%,.0f earlier", recent, earlier))
            .build();
    }

    // 3) Category concentration

    private BehavioralPattern detectCategoryConcentration(List<Transaction> txns) {
        Map<TransactionCategory, BigDecimal> byCategory = txns.stream()
            .filter(t -> t.getCategory() != null)
            .collect(Collectors.groupingBy(
                Transaction::getCategory,
                Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));

        BigDecimal total = byCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) == 0) return null;

        Optional<Map.Entry<TransactionCategory, BigDecimal>> top = byCategory.entrySet().stream()
            .max(Map.Entry.comparingByValue());
        if (top.isEmpty()) return null;

        double sharePct = top.get().getValue().doubleValue() / total.doubleValue() * 100;
        if (sharePct < CATEGORY_DOMINANCE_PCT) return null;

        TransactionCategory cat = top.get().getKey();
        String severity = sharePct >= 60 ? "HIGH" : sharePct >= 50 ? "MEDIUM" : "LOW";

        return BehavioralPattern.builder()
            .code("CATEGORY_CONCENTRATION")
            .title(cat.getDisplayName() + " dominates your spend")
            .description(String.format(
                "%s makes up %.0f%% of your total spending. Diversifying could free up cashflow.",
                cat.getDisplayName(), sharePct))
            .severity(severity)
            .value(BigDecimal.valueOf(sharePct).setScale(1, RoundingMode.HALF_UP))
            .unit("%")
            .context(String.format("₹%,.0f of ₹%,.0f total in %s",
                top.get().getValue(), total, cat.getDisplayName()))
            .build();
    }

    // 4) Increasing transaction frequency

    private BehavioralPattern detectIncreasingFrequency(List<Transaction> txns) {
        LocalDate today = LocalDate.now();
        LocalDate midpoint = today.minusMonths(3);

        long recentCount = txns.stream().filter(t -> t.getTransactionDate().isAfter(midpoint)).count();
        long earlierCount = txns.size() - recentCount;
        if (earlierCount < 5) return null;

        double ratio = (double) recentCount / earlierCount;
        if (ratio < FREQUENCY_GROWTH_RATIO) return null;

        String severity = ratio >= 1.50 ? "HIGH" : ratio >= 1.30 ? "MEDIUM" : "LOW";
        double growthPct = (ratio - 1) * 100;

        return BehavioralPattern.builder()
            .code("INCREASING_FREQUENCY")
            .title("More frequent transactions")
            .description(String.format(
                "You're spending %.0f%% more often than 3 months ago — small purchases add up.",
                growthPct))
            .severity(severity)
            .value(BigDecimal.valueOf(growthPct).setScale(1, RoundingMode.HALF_UP))
            .unit("%")
            .context(recentCount + " transactions recent vs " + earlierCount + " earlier")
            .build();
    }

    // 5) Average ticket size growth

    private BehavioralPattern detectLargeTicketTrend(List<Transaction> txns) {
        LocalDate today = LocalDate.now();
        LocalDate midpoint = today.minusMonths(3);

        DoubleSummaryStatistics recent = txns.stream()
            .filter(t -> t.getTransactionDate().isAfter(midpoint))
            .mapToDouble(t -> t.getAmount().doubleValue()).summaryStatistics();
        DoubleSummaryStatistics earlier = txns.stream()
            .filter(t -> !t.getTransactionDate().isAfter(midpoint))
            .mapToDouble(t -> t.getAmount().doubleValue()).summaryStatistics();

        if (recent.getCount() < 5 || earlier.getCount() < 5) return null;
        if (earlier.getAverage() <= 0)                       return null;

        double ratio = recent.getAverage() / earlier.getAverage();
        if (ratio < TICKET_GROWTH_RATIO) return null;

        String severity = ratio >= 1.50 ? "HIGH" : ratio >= 1.35 ? "MEDIUM" : "LOW";
        double growthPct = (ratio - 1) * 100;

        return BehavioralPattern.builder()
            .code("LARGE_TICKET_TREND")
            .title("Bigger purchases on average")
            .description(String.format(
                "Average transaction size has grown %.0f%% — single decisions cost more now.",
                growthPct))
            .severity(severity)
            .value(BigDecimal.valueOf(growthPct).setScale(1, RoundingMode.HALF_UP))
            .unit("%")
            .context(String.format("Avg ₹%,.0f recent vs ₹%,.0f earlier",
                recent.getAverage(), earlier.getAverage()))
            .build();
    }

    // Summary

    private String buildSummary(List<BehavioralPattern> patterns) {
        if (patterns.isEmpty()) {
            return "Steady spender";
        }
        BehavioralPattern top = patterns.get(0);
        return switch (top.getCode()) {
            case "WEEKEND_OVERSPEND"     -> "Weekend-driven spender";
            case "LIFESTYLE_INFLATION"   -> "Lifestyle in upward drift";
            case "CATEGORY_CONCENTRATION"-> "Single-category focused spender";
            case "INCREASING_FREQUENCY"  -> "Frequent micro-spender";
            case "LARGE_TICKET_TREND"    -> "Bigger-decision spender";
            default                       -> "Active spender";
        };
    }

    private static int severityOrder(BehavioralPattern p) {
        return switch (p.getSeverity()) {
            case "HIGH"   -> 0;
            case "MEDIUM" -> 1;
            default       -> 2;
        };
    }

    private static void addIfPresent(List<BehavioralPattern> list, BehavioralPattern p) {
        if (p != null) list.add(p);
    }
}
