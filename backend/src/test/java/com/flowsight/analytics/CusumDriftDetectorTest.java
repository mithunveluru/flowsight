package com.flowsight.analytics;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class CusumDriftDetectorTest {

    @Test
    void stableNoisySeriesRaisesNoAlarm() {
        Random rnd = new Random(7);
        double[] weekly = new double[26];
        for (int i = 0; i < weekly.length; i++) weekly[i] = 2000 + rnd.nextGaussian() * 300;

        assertThat(CusumDriftDetector.detect(weekly, 13, 50)).isEmpty();
    }

    @Test
    void sustainedStepUpIsFoundWithItsStart() {
        double[] weekly = new double[26];
        for (int i = 0; i < weekly.length; i++) weekly[i] = (i % 2 == 0 ? 900 : 1100) + (i >= 18 ? 1500 : 0);

        var shift = CusumDriftDetector.detect(weekly, 13, 50).orElseThrow();

        assertThat(shift.up()).isTrue();
        assertThat(shift.startIndex()).isEqualTo(18);
        assertThat(shift.baselineMean()).isEqualTo(1000 - 100.0 / 13, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(shift.recentMean()).isEqualTo(2500);
    }

    @Test
    void sustainedStepDownIsFound() {
        double[] weekly = new double[20];
        for (int i = 0; i < weekly.length; i++) weekly[i] = i < 12 ? 3000 : 500;

        var shift = CusumDriftDetector.detect(weekly, 10, 50).orElseThrow();

        assertThat(shift.up()).isFalse();
        assertThat(shift.startIndex()).isEqualTo(12);
    }

    @Test
    void oneOffSpikeThatRevertedIsNotReported() {
        double[] weekly = new double[26];
        for (int i = 0; i < weekly.length; i++) weekly[i] = 1000;
        weekly[14] = 4000;
        weekly[15] = 4000;

        assertThat(CusumDriftDetector.detect(weekly, 13, 50)).isEmpty();
    }
}
