package com.flowsight.analytics;

import com.flowsight.dto.analytics.*;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import com.flowsight.ml.forecast.CashFlowForecast;
import com.flowsight.ml.forecast.ForecastEvidence;
import com.flowsight.ml.forecast.ForecastInterval;
import com.flowsight.ml.forecast.ForecastService;
import com.flowsight.ml.forecast.MonthlyHistory;
import com.flowsight.ml.forecast.SeriesForecast;
import com.flowsight.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private static final int TOP_MERCHANTS  = 8;
    private static final int TREND_MONTHS   = 12;
    private static final int FORECAST_MONTHS = 3;

    // Alert thresholds
    private static final double SPIKE_RATIO     = 1.30; // 30% above average
    private static final double HIGH_RATIO      = 2.00; // 100% above average
    private static final BigDecimal SPIKE_MIN   = new BigDecimal("200"); // INR noise floor

    private static final DateTimeFormatter MONTH_KEY   = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    private final TransactionRepository transactionRepository;
    private final ForecastService       forecastService;

    // Activity bounds — surfaces "where the data is" so the UI can guide the
    // user away from an empty current-month view after a previous-month import.

    public com.flowsight.dto.analytics.ActivityBoundsResponse getActivityBounds(UUID userId) {
        java.util.Optional<LocalDate> earliest = transactionRepository.findEarliestTransactionDate(userId);
        java.util.Optional<LocalDate> latest   = transactionRepository.findLatestTransactionDate(userId);
        java.util.List<String> monthsWithActivity = transactionRepository.findMonthsWithActivity(userId);
        long totalCount = transactionRepository.countByUserId(userId);

        LocalDate now = LocalDate.now();
        LocalDate monthStart = now.withDayOfMonth(1);
        long currentMonthCount = transactionRepository.countByUserIdAndDateRange(userId, monthStart, now);

        return com.flowsight.dto.analytics.ActivityBoundsResponse.builder()
            .earliestTransactionDate(earliest.orElse(null))
            .latestTransactionDate(latest.orElse(null))
            .currentMonthHasData(currentMonthCount > 0)
            .totalTransactionCount(totalCount)
            .monthsWithActivity(monthsWithActivity)
            .build();
    }

    // Overview

    public AnalyticsOverviewResponse getOverview(UUID userId, LocalDate from, LocalDate to) {
        BigDecimal totalSpend  = coalesce(transactionRepository.sumByTypeAndDateRange(userId, TransactionType.DEBIT,  from, to));
        BigDecimal totalIncome = coalesce(transactionRepository.sumByTypeAndDateRange(userId, TransactionType.CREDIT, from, to));
        int txCount = (int) transactionRepository.countByUserIdAndDateRange(userId, from, to);

        List<CategoryBreakdownItem> breakdown = buildCategoryBreakdown(userId, from, to, totalSpend);
        List<MerchantSummary>       merchants = buildTopMerchants(userId, from, to);
        List<SpendAlert>            alerts    = buildAlerts(userId, from, to, breakdown);

        return AnalyticsOverviewResponse.builder()
            .from(from)
            .to(to)
            .totalSpend(totalSpend)
            .totalIncome(totalIncome)
            .netCashflow(totalIncome.subtract(totalSpend))
            .transactionCount(txCount)
            .categoryBreakdown(breakdown)
            .topMerchants(merchants)
            .alerts(alerts)
            .build();
    }

    // Trend + forecast

    public AnalyticsTrendResponse getTrend(UUID userId, int months) {
        int capped = Math.min(Math.max(months, 1), 24);
        LocalDate to   = LocalDate.now();
        LocalDate from = to.withDayOfMonth(1).minusMonths(capped - 1);

        List<Object[]> raw = transactionRepository.monthlyTrend(userId, from, to);
        List<MonthlyTrendPoint> points = fillMonthGaps(raw, from, to);

        MonthlyHistory history = forecastService.history(userId, to);
        CashFlowForecast forecast = forecastService.forecast(userId, history, FORECAST_MONTHS + 1);
        points.addAll(forecastPoints(forecast));

        return AnalyticsTrendResponse.builder()
            .points(points)
            .forecast(new AnalyticsTrendResponse.Forecast(forecast.asOf(), forecast.dataMonths(),
                forecast.dataMonths() <= ForecastEvidence.LOW_HISTORY_MONTHS, forecast.source(),
                ForecastEvidence.of(forecast, forecast.spend(), history),
                ForecastEvidence.of(forecast, forecast.income(), history),
                ForecastEvidence.of(forecast, forecast.net(), history)))
            .build();
    }

    // Category breakdown

    private List<CategoryBreakdownItem> buildCategoryBreakdown(
        UUID userId, LocalDate from, LocalDate to, BigDecimal totalSpend
    ) {
        List<Object[]> rows = transactionRepository.categoryBreakdown(
            userId, TransactionType.DEBIT, from, to);

        List<CategoryBreakdownItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            TransactionCategory cat    = (TransactionCategory) row[0];
            BigDecimal          amount = toBD(row[1]);
            int                 count  = toInt(row[2]);

            double pct = totalSpend.compareTo(BigDecimal.ZERO) > 0
                ? round1(amount.doubleValue() / totalSpend.doubleValue() * 100)
                : 0.0;

            items.add(CategoryBreakdownItem.builder()
                .category(cat.name())
                .displayName(cat.getDisplayName())
                .amount(amount)
                .percentage(pct)
                .transactionCount(count)
                .build());
        }
        return items;
    }

    // Top merchants

    private List<MerchantSummary> buildTopMerchants(UUID userId, LocalDate from, LocalDate to) {
        List<Object[]> rows = transactionRepository.topMerchantsRaw(
            userId, from, to, PageRequest.of(0, TOP_MERCHANTS));

        List<MerchantSummary> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(MerchantSummary.builder()
                .merchant((String) row[0])
                .totalAmount(toBD(row[1]))
                .transactionCount(toInt(row[2]))
                .build());
        }
        return items;
    }

    // Behavioral alerts

    private List<SpendAlert> buildAlerts(
        UUID userId, LocalDate from, LocalDate to, List<CategoryBreakdownItem> current
    ) {
        // Only alert for periods that include recent data
        if (to.isBefore(LocalDate.now().minusDays(45))) return List.of();
        if (current.isEmpty()) return List.of();

        // Historical baseline: 3 months before the query period
        LocalDate histTo   = from.minusDays(1);
        LocalDate histFrom = histTo.withDayOfMonth(1).minusMonths(2); // 3-month window

        List<Object[]> histRows = transactionRepository.categoryBreakdown(
            userId, TransactionType.DEBIT, histFrom, histTo);

        if (histRows.isEmpty()) return List.of(); // not enough history

        // Average monthly spend per category over the historical window
        Map<String, BigDecimal> historicalAvg = new HashMap<>();
        for (Object[] row : histRows) {
            TransactionCategory cat    = (TransactionCategory) row[0];
            BigDecimal          total  = toBD(row[1]);
            historicalAvg.put(cat.name(), total.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP));
        }

        List<SpendAlert> alerts = new ArrayList<>();
        for (CategoryBreakdownItem item : current) {
            BigDecimal avg = historicalAvg.getOrDefault(item.getCategory(), BigDecimal.ZERO);
            if (avg.compareTo(BigDecimal.ZERO) == 0) continue;
            if (item.getAmount().compareTo(SPIKE_MIN) < 0) continue;

            double ratio = item.getAmount().doubleValue() / avg.doubleValue();
            if (ratio < SPIKE_RATIO) continue;

            String severity = ratio >= HIGH_RATIO ? "HIGH" : "MEDIUM";
            double changePct = round1((ratio - 1) * 100);

            alerts.add(SpendAlert.builder()
                .type("CATEGORY_SPIKE")
                .severity(severity)
                .category(item.getCategory())
                .categoryDisplayName(item.getDisplayName())
                .message(String.format("%.0f%% above your 3-month average", changePct))
                .currentAmount(item.getAmount())
                .averageAmount(avg)
                .changePercent(changePct)
                .build());
        }

        return alerts.stream()
            .sorted(Comparator.comparingDouble(SpendAlert::getChangePercent).reversed())
            .limit(3)
            .collect(Collectors.toList());
    }

    // Monthly trend helpers

    private List<MonthlyTrendPoint> fillMonthGaps(List<Object[]> raw, LocalDate from, LocalDate to) {
        Map<String, Object[]> map = new LinkedHashMap<>();
        for (Object[] row : raw) map.put((String) row[0], row);

        List<MonthlyTrendPoint> points = new ArrayList<>();
        LocalDate cur = from.withDayOfMonth(1);
        LocalDate end = to.withDayOfMonth(1);

        while (!cur.isAfter(end)) {
            String key   = cur.format(MONTH_KEY);
            String label = cur.format(MONTH_LABEL);
            Object[] row = map.get(key);

            BigDecimal spend  = row != null ? toBD(row[1]) : BigDecimal.ZERO;
            BigDecimal income = row != null ? toBD(row[2]) : BigDecimal.ZERO;

            points.add(MonthlyTrendPoint.builder()
                .month(key).label(label)
                .spend(spend).income(income)
                .net(income.subtract(spend))
                .projected(false)
                .build());

            cur = cur.plusMonths(1);
        }
        return points;
    }

    // the months after the current one; step 1 of the forecast is the current month
    private List<MonthlyTrendPoint> forecastPoints(CashFlowForecast forecast) {
        List<MonthlyTrendPoint> out = new ArrayList<>();
        for (int k = 2; k <= FORECAST_MONTHS + 1; k++) {
            LocalDate m = forecast.month(k).atDay(1);
            SeriesForecast.Step spend  = forecast.spend().step(k);
            SeriesForecast.Step income = forecast.income().step(k);
            SeriesForecast.Step net    = forecast.net().step(k);
            out.add(MonthlyTrendPoint.builder()
                .month(m.format(MONTH_KEY)).label(m.format(MONTH_LABEL))
                .spend(money(spend.point())).income(money(income.point())).net(money(net.point()))
                .projected(true)
                .range(new MonthlyTrendPoint.Range(
                    ForecastInterval.of(spend), ForecastInterval.of(income), ForecastInterval.of(net)))
                .build());
        }
        return out;
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal coalesce(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal toBD(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        try { return new BigDecimal(o.toString()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
