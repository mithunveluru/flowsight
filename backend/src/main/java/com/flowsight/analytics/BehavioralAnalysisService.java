package com.flowsight.analytics;

import com.flowsight.dto.insights.BehavioralPattern;
import com.flowsight.dto.insights.BehavioralProfile;
import com.flowsight.dto.insights.CategoryDrift;
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
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects behavioral spending patterns from up to 6 months of spend (debits other than
 * transfers). Weekend overspend and lifestyle inflation are judged against thresholds derived
 * from the user's own week-to-week and month-to-month variability; category drift uses CUSUM.
 * Each detector runs only when the history is long enough for its comparison:
 * weekend 8 weeks, category share 4 weeks, drift 16 weeks, trends 6 months.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BehavioralAnalysisService {

    private static final int LOOKBACK_MONTHS = 6;

    static final int MIN_DAYS_WEEKEND = 56;
    static final int MIN_DAYS_CONCENTRATION = 28;
    static final int MIN_DAYS_DRIFT = 112;
    // six months less a week of grace, so a first import on the 3rd still counts
    static final int MIN_DAYS_TREND = 175;

    private static final double Z = 2.0;
    private static final double WEEKEND_FLOOR_RATIO = 1.25;
    private static final double WEEKEND_MAX_RATIO   = 3.0;
    private static final double INFLATION_FLOOR     = 0.10;
    private static final double INFLATION_MAX       = 1.0;
    private static final double WEEKEND_DAYS_PER_MONTH = 104.0 / 12;
    private static final int    MIN_WEEKEND_WEEKS = 6;
    private static final int    DRIFT_WEEKS = 26;
    private static final double DRIFT_SIGMA_FLOOR = 50;
    private static final double DRIFT_MIN_MONTHLY_CHANGE = 500;
    private static final int    MAX_DRIFTS = 5;

    private static final double CATEGORY_DOMINANCE_PCT = 40.0;
    private static final double FREQUENCY_GROWTH_RATIO = 1.20;
    private static final double TICKET_GROWTH_RATIO    = 1.20;

    private static final Set<TransactionCategory> NOT_DRIFT = EnumSet.of(
        TransactionCategory.TRANSFER, TransactionCategory.INCOME, TransactionCategory.UNCATEGORIZED);

    private final TransactionRepository transactionRepository;

    public BehavioralProfile analyze(UUID userId) {
        LocalDate today = LocalDate.now();
        List<Transaction> txns = transactionRepository.findForExport(
            userId, null, today.minusMonths(LOOKBACK_MONTHS), today);
        LocalDate earliest = transactionRepository.findEarliestTransactionDate(userId).orElse(null);
        return profile(txns, earliest, today);
    }

    BehavioralProfile profile(List<Transaction> txns, LocalDate earliest, LocalDate today) {
        List<Transaction> spend = txns.stream().filter(BehavioralAnalysisService::isSpend).toList();
        int historyDays = earliest == null ? 0 : (int) Math.max(0, ChronoUnit.DAYS.between(earliest, today));
        LocalDate windowStart = today.minusMonths(LOOKBACK_MONTHS);
        LocalDate start = earliest == null || earliest.isBefore(windowStart) ? windowStart : earliest;

        List<BehavioralPattern> patterns = new ArrayList<>();
        List<String> needsMoreHistory = new ArrayList<>();
        List<CategoryDrift> drifts = List.of();

        if (historyDays >= MIN_DAYS_WEEKEND) {
            addIfPresent(patterns, detectWeekendOverspend(spend, start, today));
        } else {
            needsMoreHistory.add("WEEKEND_OVERSPEND");
        }
        if (historyDays >= MIN_DAYS_TREND) {
            addIfPresent(patterns, detectLifestyleInflation(spend, today));
            addIfPresent(patterns, detectIncreasingFrequency(spend, today));
            addIfPresent(patterns, detectLargeTicketTrend(spend, today));
        } else {
            needsMoreHistory.addAll(List.of("LIFESTYLE_INFLATION", "INCREASING_FREQUENCY", "LARGE_TICKET_TREND"));
        }
        if (historyDays >= MIN_DAYS_CONCENTRATION) {
            addIfPresent(patterns, detectCategoryConcentration(spend));
        } else {
            needsMoreHistory.add("CATEGORY_CONCENTRATION");
        }
        if (historyDays >= MIN_DAYS_DRIFT) {
            int weeks = Math.min(DRIFT_WEEKS, historyDays / 7);
            drifts = detectDrifts(spend, today, weeks);
            addIfPresent(patterns, driftPattern(drifts, spend, today, weeks));
        } else {
            needsMoreHistory.add("CATEGORY_DRIFT");
        }

        // HIGH first
        patterns.sort(Comparator.comparingInt(BehavioralAnalysisService::severityOrder));

        return BehavioralProfile.builder()
            .summary(buildSummary(patterns))
            .patterns(patterns)
            .historyDays(historyDays)
            .needsMoreHistory(needsMoreHistory)
            .drifts(drifts)
            .build();
    }

    static boolean isSpend(Transaction t) {
        return t.getType() == TransactionType.DEBIT && t.getCategory() != TransactionCategory.TRANSFER;
    }

    // Per-week log ratio of weekend to weekday daily spend; flagged when the observed ratio clears
    // max(1.25, 1.25 * e^(z * sd / sqrt(weeks))), so erratic weeks need a bigger gap.
    private BehavioralPattern detectWeekendOverspend(List<Transaction> spend, LocalDate start, LocalDate today) {
        LocalDate firstMonday = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate lastSunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        int weeks = (int) (ChronoUnit.DAYS.between(firstMonday, lastSunday) + 1) / 7;
        if (weeks < MIN_WEEKEND_WEEKS) return null;

        double[] weekend = new double[weeks];
        double[] weekday = new double[weeks];
        for (Transaction tx : spend) {
            LocalDate d = tx.getTransactionDate();
            if (d.isBefore(firstMonday) || d.isAfter(lastSunday)) continue;
            int w = (int) (ChronoUnit.DAYS.between(firstMonday, d) / 7);
            if (w >= weeks) continue;
            if (isWeekend(d)) weekend[w] += tx.getAmount().doubleValue();
            else weekday[w] += tx.getAmount().doubleValue();
        }
        double weekendTotal = Arrays.stream(weekend).sum();
        double weekdayTotal = Arrays.stream(weekday).sum();
        if (weekdayTotal <= 0) return null;

        double weekendDaily = weekendTotal / (2.0 * weeks);
        double weekdayDaily = weekdayTotal / (5.0 * weeks);
        // smoothing keeps a week with no weekday spend from producing an infinite ratio
        double delta = Math.max(1, 0.05 * (weekendTotal + weekdayTotal) / (7.0 * weeks));
        double[] logRatios = new double[weeks];
        for (int w = 0; w < weeks; w++) {
            logRatios[w] = Math.log((weekend[w] / 2 + delta) / (weekday[w] / 5 + delta));
        }
        double threshold = Math.min(WEEKEND_MAX_RATIO,
            WEEKEND_FLOOR_RATIO * Math.exp(Z * sampleSd(logRatios) / Math.sqrt(weeks)));
        double ratio = weekendDaily / weekdayDaily;
        if (ratio < threshold) return null;

        String severity = ratio >= threshold * 1.4 ? "HIGH" : ratio >= threshold * 1.2 ? "MEDIUM" : "LOW";
        double overspendPct = (ratio - 1) * 100;
        double thresholdPct = (threshold - 1) * 100;
        double saving = (weekendDaily - weekdayDaily * threshold) * WEEKEND_DAYS_PER_MONTH;

        return BehavioralPattern.builder()
            .code("WEEKEND_OVERSPEND")
            .title("Weekend-driven spending")
            .description(String.format(
                "You spend %.0f%% more per day on weekends than weekdays.", overspendPct))
            .severity(severity)
            .value(scale1(overspendPct))
            .unit("%")
            .context(String.format(
                "₹%,.0f/day weekend vs ₹%,.0f/day weekday over %d weeks; your threshold is %.0f%% more",
                weekendDaily, weekdayDaily, weeks, thresholdPct))
            .threshold(scale1(thresholdPct))
            .monthlySavingEstimate(money(saving))
            .build();
    }

    // Last 90 days vs the 90 before, in 30-day buckets; flagged when growth clears
    // max(10%, z * CV * sqrt(2/3)), the noise in a difference of two 3-bucket means.
    private BehavioralPattern detectLifestyleInflation(List<Transaction> spend, LocalDate today) {
        double[] buckets = new double[6];
        for (Transaction tx : spend) {
            long age = ChronoUnit.DAYS.between(tx.getTransactionDate(), today);
            if (age < 0 || age >= 180) continue;
            buckets[(int) (age / 30)] += tx.getAmount().doubleValue();
        }
        double[] recent = {buckets[0], buckets[1], buckets[2]};
        double[] earlier = {buckets[3], buckets[4], buckets[5]};
        double recentMean = mean(recent);
        double earlierMean = mean(earlier);
        if (earlierMean <= 0) return null;

        double pooledSd = Math.sqrt((Math.pow(sampleSd(recent), 2) + Math.pow(sampleSd(earlier), 2)) / 2);
        double growthThreshold = Math.min(INFLATION_MAX,
            Math.max(INFLATION_FLOOR, Z * pooledSd / earlierMean * Math.sqrt(2.0 / 3)));
        double ratio = recentMean / earlierMean;
        if (ratio < 1 + growthThreshold) return null;

        double excess = ratio - 1 - growthThreshold;
        String severity = excess >= 0.25 ? "HIGH" : excess >= 0.10 ? "MEDIUM" : "LOW";
        double inflationPct = (ratio - 1) * 100;
        double saving = recentMean - earlierMean * (1 + growthThreshold);

        return BehavioralPattern.builder()
            .code("LIFESTYLE_INFLATION")
            .title("Lifestyle inflation detected")
            .description(String.format(
                "Your spending has grown %.0f%% over the last 3 months vs the prior 3.", inflationPct))
            .severity(severity)
            .value(scale1(inflationPct))
            .unit("%")
            .context(String.format(
                "₹%,.0f/month recently vs ₹%,.0f/month before; your threshold is %.0f%% growth",
                recentMean, earlierMean, growthThreshold * 100))
            .threshold(scale1(growthThreshold * 100))
            .monthlySavingEstimate(money(saving))
            .build();
    }

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

    private BehavioralPattern detectIncreasingFrequency(List<Transaction> txns, LocalDate today) {
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

    private BehavioralPattern detectLargeTicketTrend(List<Transaction> txns, LocalDate today) {
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

    // Weekly spend per category over the last `weeks` weeks ending today; the first half is the reference.
    private List<CategoryDrift> detectDrifts(List<Transaction> spend, LocalDate today, int weeks) {
        Map<TransactionCategory, double[]> series = weeklySeries(spend, today, weeks);
        int reference = weeks / 2;
        LocalDate firstDay = today.minusDays(7L * weeks - 1);
        List<CategoryDrift> out = new ArrayList<>();
        for (Map.Entry<TransactionCategory, double[]> e : series.entrySet()) {
            Optional<CusumDriftDetector.Shift> shift = CusumDriftDetector.detect(e.getValue(), reference, DRIFT_SIGMA_FLOOR);
            if (shift.isEmpty()) continue;
            CusumDriftDetector.Shift s = shift.get();
            double monthly = (s.recentMean() - s.baselineMean()) * 52 / 12;
            if (Math.abs(monthly) < DRIFT_MIN_MONTHLY_CHANGE) continue;
            out.add(CategoryDrift.builder()
                .category(e.getKey().name())
                .displayName(e.getKey().getDisplayName())
                .direction(s.up() ? "UP" : "DOWN")
                .since(firstDay.plusDays(7L * s.startIndex()))
                .weeksSince(weeks - s.startIndex())
                .baselineWeekly(money(s.baselineMean()))
                .recentWeekly(money(s.recentMean()))
                .monthlyChange(money(monthly))
                .build());
        }
        out.sort(Comparator.comparing((CategoryDrift d) -> d.getMonthlyChange().abs()).reversed());
        return out.size() > MAX_DRIFTS ? List.copyOf(out.subList(0, MAX_DRIFTS)) : out;
    }

    private BehavioralPattern driftPattern(List<CategoryDrift> drifts, List<Transaction> spend, LocalDate today, int weeks) {
        CategoryDrift top = drifts.stream().filter(d -> "UP".equals(d.getDirection())).findFirst().orElse(null);
        if (top == null) return null;
        double[] weekly = weeklySeries(spend, today, weeks).get(TransactionCategory.valueOf(top.getCategory()));
        double sigmas = CusumDriftDetector.detect(weekly, weeks / 2, DRIFT_SIGMA_FLOOR)
            .map(CusumDriftDetector.Shift::sigmas).orElse(0.0);
        String severity = sigmas >= 3 ? "HIGH" : sigmas >= 2 ? "MEDIUM" : "LOW";
        return BehavioralPattern.builder()
            .code("CATEGORY_DRIFT")
            .title(top.getDisplayName() + " spending has shifted up")
            .description(String.format(
                "Since %s you have spent about ₹%,.0f a week on %s, up from ₹%,.0f a week before.",
                top.getSince(), top.getRecentWeekly().doubleValue(), top.getDisplayName(),
                top.getBaselineWeekly().doubleValue()))
            .severity(severity)
            .value(top.getMonthlyChange())
            .unit("₹")
            .context(String.format("₹%,.0f more per month over the last %d weeks",
                top.getMonthlyChange().doubleValue(), top.getWeeksSince()))
            .category(top.getCategory())
            .monthlySavingEstimate(top.getMonthlyChange())
            .build();
    }

    private static Map<TransactionCategory, double[]> weeklySeries(List<Transaction> spend, LocalDate today, int weeks) {
        LocalDate firstDay = today.minusDays(7L * weeks - 1);
        Map<TransactionCategory, double[]> series = new EnumMap<>(TransactionCategory.class);
        for (Transaction tx : spend) {
            TransactionCategory cat = tx.getCategory();
            if (cat == null || NOT_DRIFT.contains(cat)) continue;
            LocalDate d = tx.getTransactionDate();
            if (d.isBefore(firstDay) || d.isAfter(today)) continue;
            int w = (int) (ChronoUnit.DAYS.between(firstDay, d) / 7);
            series.computeIfAbsent(cat, c -> new double[weeks])[w] += tx.getAmount().doubleValue();
        }
        return series;
    }

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
            case "CATEGORY_DRIFT"        -> "Spending pattern on the move";
            default                       -> "Active spender";
        };
    }

    private static boolean isWeekend(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    private static double mean(double[] xs) {
        return Arrays.stream(xs).average().orElse(0);
    }

    private static double sampleSd(double[] xs) {
        if (xs.length < 2) return 0;
        double m = mean(xs);
        double ss = 0;
        for (double x : xs) ss += (x - m) * (x - m);
        return Math.sqrt(ss / (xs.length - 1));
    }

    private static BigDecimal scale1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
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
