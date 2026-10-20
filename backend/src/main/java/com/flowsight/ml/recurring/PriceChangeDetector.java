package com.flowsight.ml.recurring;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Finds a step up in a recurring payment's price. The earliest split where at least two
 * earlier payments form a stable baseline (median and MAD) and every later payment, at least
 * two of them, sits at least {@link #MIN_Z} robust deviations above it and at least
 * {@link #MIN_RATIO} times its median, while the later payments agree with each other.
 * Metered bills fail the baseline test, so seasonal swings are not reported as price rises.
 */
public final class PriceChangeDetector {

    static final int    MIN_BEFORE  = 2;
    static final int    MIN_AFTER   = 2;
    static final double MIN_Z       = 3.0;
    static final double MIN_RATIO   = 1.05;
    // later payments within 5% of their median count as one new price
    static final double AFTER_SPREAD = 0.05;
    // a fixed price has MAD 0; 1% of the price stands in so a one-rupee rounding is not a step
    static final double SCALE_FLOOR = 0.01;
    // 1.4826 * MAD estimates the standard deviation of normally distributed noise
    private static final double MAD_TO_SD = 1.4826;

    private PriceChangeDetector() {
    }

    public record Payment(LocalDate date, double amount) {
    }

    /** confidence = 1 - product of 1/z^2 over the later payments (Chebyshev bound per payment). */
    public record Step(LocalDate changeDate, double before, double after, int paymentsBefore, int paymentsAfter,
                       double minZ, double confidence) {

        public double ratio() {
            return after / before;
        }
    }

    /** payments oldest first. */
    public static Optional<Step> detect(List<Payment> payments) {
        int n = payments.size();
        double[] amounts = payments.stream().mapToDouble(Payment::amount).toArray();
        for (int k = MIN_BEFORE; k <= n - MIN_AFTER; k++) {
            double[] before = Arrays.copyOfRange(amounts, 0, k);
            double[] after = Arrays.copyOfRange(amounts, k, n);
            double mb = median(before);
            double ma = median(after);
            if (mb <= 0 || ma / mb < MIN_RATIO) continue;
            if (mad(after) > AFTER_SPREAD * ma) continue;
            double scale = Math.max(MAD_TO_SD * mad(before), SCALE_FLOOR * mb);
            double minZ = Double.POSITIVE_INFINITY;
            double tail = 1.0;
            for (double a : after) {
                double z = (a - mb) / scale;
                minZ = Math.min(minZ, z);
                tail *= 1.0 / (z * z);
            }
            if (minZ < MIN_Z) continue;
            return Optional.of(new Step(payments.get(k).date(), mb, ma, k, n - k, minZ, Math.max(0, 1 - tail)));
        }
        return Optional.empty();
    }

    static double median(double[] values) {
        double[] s = values.clone();
        Arrays.sort(s);
        int m = s.length / 2;
        return s.length % 2 == 1 ? s[m] : (s[m - 1] + s[m]) / 2;
    }

    static double mad(double[] values) {
        double med = median(values);
        double[] dev = new double[values.length];
        for (int i = 0; i < values.length; i++) dev[i] = Math.abs(values[i] - med);
        return median(dev);
    }
}
