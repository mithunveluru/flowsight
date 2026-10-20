package com.flowsight.analytics;

import java.util.Optional;

/**
 * Two-sided tabular CUSUM on a weekly series. The first referenceWeeks set the user's own
 * mean and spread; the rest are monitored. k = 0.5 sigma and h = 5 sigma are the textbook
 * pair (Montgomery): a one-sigma shift is flagged after about ten weeks on average, a stable
 * roughly normal series about once in 230 weeks (two-sided). A shift is reported only if it
 * is still in effect at the end (median of the weeks since its start).
 */
final class CusumDriftDetector {

    static final double K = 0.5;
    static final double H = 5.0;

    record Shift(boolean up, int startIndex, double baselineMean, double sigma, double recentMean) {
        double sigmas() {
            return Math.abs(recentMean - baselineMean) / sigma;
        }
    }

    private CusumDriftDetector() {
    }

    static Optional<Shift> detect(double[] weekly, int referenceWeeks, double sigmaFloor) {
        if (referenceWeeks < 2 || weekly.length <= referenceWeeks) return Optional.empty();
        double mean = 0;
        for (int i = 0; i < referenceWeeks; i++) mean += weekly[i];
        mean /= referenceWeeks;
        double var = 0;
        for (int i = 0; i < referenceWeeks; i++) var += (weekly[i] - mean) * (weekly[i] - mean);
        // weekly spend is lumpy; a near-constant reference would make any change look huge
        double sigma = Math.max(Math.sqrt(var / (referenceWeeks - 1)), Math.max(0.25 * mean, sigmaFloor));

        double up = 0, down = 0;
        int upStart = referenceWeeks, downStart = referenceWeeks;
        int upAlarm = -1, downAlarm = -1;
        for (int t = referenceWeeks; t < weekly.length; t++) {
            double z = (weekly[t] - mean) / sigma;
            if (upAlarm < 0) {
                if (up == 0) upStart = t;
                up = Math.max(0, up + z - K);
                if (up > H) upAlarm = t;
            }
            if (downAlarm < 0) {
                if (down == 0) downStart = t;
                down = Math.max(0, down - z - K);
                if (down > H) downAlarm = t;
            }
        }

        Shift best = null;
        if (upAlarm >= 0) best = shift(true, upStart, weekly, mean, sigma);
        if (downAlarm >= 0) {
            Shift d = shift(false, downStart, weekly, mean, sigma);
            if (best == null || d.sigmas() > best.sigmas()) best = d;
        }
        if (best == null) return Optional.empty();
        // the median, not the mean, decides persistence: a two-week spike must not read as a new level
        double level = median(weekly, best.startIndex());
        boolean inEffect = best.up() ? level - mean > K * sigma : mean - level > K * sigma;
        return inEffect ? Optional.of(best) : Optional.empty();
    }

    private static double median(double[] weekly, int from) {
        double[] tail = java.util.Arrays.copyOfRange(weekly, from, weekly.length);
        java.util.Arrays.sort(tail);
        int n = tail.length;
        return n % 2 == 1 ? tail[n / 2] : (tail[n / 2 - 1] + tail[n / 2]) / 2;
    }

    private static Shift shift(boolean up, int start, double[] weekly, double mean, double sigma) {
        double sum = 0;
        for (int i = start; i < weekly.length; i++) sum += weekly[i];
        return new Shift(up, start, mean, sigma, sum / (weekly.length - start));
    }
}
