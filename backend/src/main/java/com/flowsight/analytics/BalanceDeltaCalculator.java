package com.flowsight.analytics;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reconstructs transaction amounts from a sequence of running balances.
 *
 * <p>Given an ordered list of {@link BalanceRow} entries (date, description,
 * balance), this calculator emits a {@link ReconstructedTransaction} for every
 * pair of consecutive rows where the balance changes. Opening / closing
 * balance markers seed the running anchor but do not produce transactions.
 *
 * <p>Each reconstructed transaction carries:
 * <ul>
 *   <li>{@code amount} = |currentBalance - previousBalance|</li>
 *   <li>{@code isDebit} = true when the delta is negative (balance decreased)</li>
 *   <li>{@code confidence} in [0,1] reflecting jump size relative to the
 *       observed median, presence of warnings, and whether the row was
 *       skipped.</li>
 * </ul>
 *
 * <p>The calculator is stateless across invocations - all state lives in
 * the parameter list.
 */
@Service
@Slf4j
public class BalanceDeltaCalculator {

    /** Multiplier above the median absolute delta after which we flag a jump as suspicious. */
    private static final BigDecimal SUSPICIOUS_JUMP_MULTIPLIER = new BigDecimal("100");

    /** Minimum absolute delta in INR below which the jump check is skipped (tiny txns are always fine). */
    private static final BigDecimal MIN_DELTA_FOR_JUMP_CHECK = new BigDecimal("1000");

    /** Confidence applied to a normally reconstructed row with no warnings. */
    private static final BigDecimal CONFIDENCE_FULL = new BigDecimal("1.0000");

    /** Confidence applied to a row flagged as a suspicious jump. */
    private static final BigDecimal CONFIDENCE_SUSPICIOUS = new BigDecimal("0.5000");

    private static final Set<String> OPENING_MARKERS = Set.of(
        "opening balance", "opening bal", "ob", "balance forward", "b/f", "brought forward",
        "previous balance", "carry forward"
    );
    private static final Set<String> CLOSING_MARKERS = Set.of(
        "closing balance", "closing bal", "cb", "c/f", "carried forward", "ending balance"
    );

    public Result reconstruct(List<BalanceRow> rawRows) {
        List<String> warnings = new ArrayList<>();
        if (rawRows == null || rawRows.isEmpty()) {
            return new Result(List.of(), warnings);
        }

        // 1. Drop rows with no balance, then deduplicate, then order.
        List<BalanceRow> rows = new ArrayList<>(rawRows.size());
        Set<String> seen = new HashSet<>();
        for (BalanceRow r : rawRows) {
            if (r.getBalance() == null || r.getDate() == null) {
                warnings.add("Row " + r.getSourceRowNumber() + ": missing date or balance, skipped");
                continue;
            }
            String key = r.getDate() + "|" + nullSafe(r.getDescription()).toLowerCase(Locale.ROOT)
                + "|" + r.getBalance().stripTrailingZeros().toPlainString();
            if (!seen.add(key)) {
                warnings.add("Row " + r.getSourceRowNumber() + ": duplicate row, skipped");
                continue;
            }
            rows.add(r);
        }
        rows.sort(Comparator
            .comparing(BalanceRow::getDate)
            .thenComparingInt(BalanceRow::getSourceRowNumber));

        // 2. Pre-compute median absolute delta for the suspicious-jump heuristic.
        BigDecimal medianDelta = computeMedianAbsoluteDelta(rows);

        // 3. Walk rows, anchoring on opening balance markers and skipping closing markers.
        List<ReconstructedTransaction> txns = new ArrayList<>();
        BigDecimal previous = null;
        for (BalanceRow row : rows) {
            String descLower = nullSafe(row.getDescription()).toLowerCase(Locale.ROOT);

            // Opening / closing markers seed the anchor without emitting a transaction.
            if (matchesAny(descLower, OPENING_MARKERS) || matchesAny(descLower, CLOSING_MARKERS)) {
                previous = row.getBalance();
                continue;
            }
            // The first real row anchors silently when no explicit opening marker is present.
            if (previous == null) {
                previous = row.getBalance();
                continue;
            }

            BigDecimal delta = row.getBalance().subtract(previous);
            if (delta.signum() == 0) {
                // Zero-value rows (fee reversals, info markers) are ignored - existing
                // pipelines treat zero-amount rows as noise.
                previous = row.getBalance();
                continue;
            }

            boolean suspicious = isSuspiciousJump(delta.abs(), medianDelta);
            if (suspicious) {
                warnings.add("Row " + row.getSourceRowNumber()
                    + ": suspicious balance jump of " + delta.abs() + " (median ~" + medianDelta + ")");
            }

            BigDecimal amount = delta.abs().setScale(2, RoundingMode.HALF_UP);
            boolean isDebit = delta.signum() < 0;
            BigDecimal confidence = suspicious ? CONFIDENCE_SUSPICIOUS : CONFIDENCE_FULL;

            txns.add(ReconstructedTransaction.builder()
                .date(row.getDate())
                .description(row.getDescription())
                .amount(amount)
                .isDebit(isDebit)
                .confidence(confidence)
                .suspicious(suspicious)
                .rawText(row.getRawText())
                .sourceRowNumber(row.getSourceRowNumber())
                .build());

            previous = row.getBalance();
        }

        return new Result(txns, warnings);
    }

    private BigDecimal computeMedianAbsoluteDelta(List<BalanceRow> rows) {
        if (rows.size() < 2) return BigDecimal.ZERO;
        List<BigDecimal> deltas = new ArrayList<>(rows.size() - 1);
        for (int i = 1; i < rows.size(); i++) {
            BigDecimal prev = rows.get(i - 1).getBalance();
            BigDecimal cur  = rows.get(i).getBalance();
            BigDecimal d = cur.subtract(prev).abs();
            if (d.signum() > 0) deltas.add(d);
        }
        if (deltas.isEmpty()) return BigDecimal.ZERO;
        deltas.sort(BigDecimal::compareTo);
        return deltas.get(deltas.size() / 2);
    }

    private boolean isSuspiciousJump(BigDecimal absDelta, BigDecimal medianDelta) {
        if (medianDelta.signum() <= 0) return false;
        if (absDelta.compareTo(MIN_DELTA_FOR_JUMP_CHECK) < 0) return false;
        return absDelta.compareTo(medianDelta.multiply(SUSPICIOUS_JUMP_MULTIPLIER)) > 0;
    }

    private boolean matchesAny(String descLower, Set<String> markers) {
        if (descLower == null || descLower.isBlank()) return false;
        for (String m : markers) {
            if (descLower.contains(m)) return true;
        }
        return false;
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    @Value
    @Builder
    public static class BalanceRow {
        LocalDate date;
        String description;
        BigDecimal balance;
        int sourceRowNumber;
        String rawText;
    }

    @Value
    @Builder
    public static class ReconstructedTransaction {
        LocalDate date;
        String description;
        BigDecimal amount;
        boolean isDebit;
        BigDecimal confidence;
        boolean suspicious;
        String rawText;
        int sourceRowNumber;
    }

    @Value
    public static class Result {
        List<ReconstructedTransaction> transactions;
        List<String> warnings;
    }
}
