package com.flowsight.ml.recurring;

import com.flowsight.entity.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * What "small" and "adds up" mean for one user's habit drains. Personal baselines come from
 * the user's own debits over the last six months: a small purchase costs at most the median,
 * across merchants, of each merchant's median ticket, and a habit matters once it costs
 * {@link #MATERIAL_SHARE} of their median monthly outflow. With personalization off, or too
 * little history, the global defaults apply, which are the rule thresholds.
 */
public record SpendBaseline(BigDecimal maxTicket, BigDecimal minMonthly, boolean personal) {

    public static final BigDecimal GLOBAL_MAX_TICKET  = new BigDecimal("500");
    public static final BigDecimal GLOBAL_MIN_MONTHLY = new BigDecimal("1500");
    static final double MATERIAL_SHARE = 0.01;
    static final int    HISTORY_MONTHS = 6;
    static final int    MIN_MONTHS     = 2;

    public static SpendBaseline global() {
        return new SpendBaseline(GLOBAL_MAX_TICKET, GLOBAL_MIN_MONTHLY, false);
    }

    /** debits: the user's debits up to asOf; complete calendar months only count toward outflow. */
    public static SpendBaseline personal(List<Transaction> debits, LocalDate asOf) {
        LocalDate from = asOf.minusMonths(HISTORY_MONTHS);
        YearMonth current = YearMonth.from(asOf);
        Map<String, List<Double>> byMerchant = new TreeMap<>();
        for (Transaction t : debits) {
            if (t.getTransactionDate().isBefore(from) || t.getTransactionDate().isAfter(asOf)) continue;
            String merchant = t.getMerchant() == null ? "" : t.getMerchant().toLowerCase(Locale.ROOT);
            byMerchant.computeIfAbsent(merchant, k -> new ArrayList<>()).add(t.getAmount().doubleValue());
        }
        // each merchant's median counts once, so the habit being measured cannot set its own bar
        double[] tickets = byMerchant.values().stream()
            .mapToDouble(v -> PriceChangeDetector.median(v.stream().mapToDouble(Double::doubleValue).toArray()))
            .toArray();
        Map<YearMonth, Double> monthly = new TreeMap<>();
        for (Transaction t : debits) {
            YearMonth m = YearMonth.from(t.getTransactionDate());
            if (t.getTransactionDate().isBefore(from) || !m.isBefore(current)) continue;
            monthly.merge(m, t.getAmount().doubleValue(), Double::sum);
        }
        // the oldest month is usually cut by the window start
        monthly.remove(YearMonth.from(from));
        if (tickets.length == 0 || monthly.size() < MIN_MONTHS) return global();
        double outflow = PriceChangeDetector.median(monthly.values().stream().mapToDouble(Double::doubleValue).toArray());
        return new SpendBaseline(
            BigDecimal.valueOf(PriceChangeDetector.median(tickets)).setScale(2, RoundingMode.HALF_UP),
            BigDecimal.valueOf(outflow * MATERIAL_SHARE).setScale(2, RoundingMode.HALF_UP),
            true);
    }
}
