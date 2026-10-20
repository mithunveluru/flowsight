package com.flowsight.ml.recurring;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceChangeDetectorTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 5);

    private static List<PriceChangeDetector.Payment> monthly(double... amounts) {
        List<PriceChangeDetector.Payment> out = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) out.add(new PriceChangeDetector.Payment(START.plusMonths(i), amounts[i]));
        return out;
    }

    @Test
    void twoPaymentsAfterAStepAreEnough() {
        PriceChangeDetector.Step step = PriceChangeDetector.detect(monthly(499, 499, 649, 649)).orElseThrow();
        assertThat(step.changeDate()).isEqualTo(START.plusMonths(2));
        assertThat(step.before()).isEqualTo(499);
        assertThat(step.after()).isEqualTo(649);
        assertThat(step.paymentsBefore()).isEqualTo(2);
        assertThat(step.paymentsAfter()).isEqualTo(2);
        assertThat(step.confidence()).isGreaterThan(0.99);
    }

    @Test
    void theEarliestSplitWinsOverOneWithOldPricesBehindIt() {
        PriceChangeDetector.Step step = PriceChangeDetector.detect(monthly(199, 199, 199, 199, 249, 249, 249)).orElseThrow();
        assertThat(step.paymentsAfter()).isEqualTo(3);
    }

    @Test
    void aSingleHigherPaymentIsNotAStep() {
        assertThat(PriceChangeDetector.detect(monthly(499, 499, 499, 649))).isEmpty();
        assertThat(PriceChangeDetector.detect(monthly(499, 499, 649, 499))).isEmpty();
    }

    @Test
    void meteredBillsWithASeasonalRiseAreNotSteps() {
        assertThat(PriceChangeDetector.detect(monthly(1400, 2100, 1650, 1300, 1900, 2300, 2400))).isEmpty();
    }

    @Test
    void roundingNoiseAndSmallRisesAreIgnored() {
        assertThat(PriceChangeDetector.detect(monthly(499, 500, 499, 501, 500))).isEmpty();
        assertThat(PriceChangeDetector.detect(monthly(1000, 1000, 1000, 1030, 1030))).isEmpty();
    }

    @Test
    void medianAndMad() {
        assertThat(PriceChangeDetector.median(new double[]{3, 1, 2})).isEqualTo(2);
        assertThat(PriceChangeDetector.median(new double[]{4, 1, 2, 3})).isEqualTo(2.5);
        assertThat(PriceChangeDetector.mad(new double[]{1, 1, 2, 2, 4, 6, 9})).isEqualTo(1);
    }
}
