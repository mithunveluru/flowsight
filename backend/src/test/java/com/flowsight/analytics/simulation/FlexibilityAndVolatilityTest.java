package com.flowsight.analytics.simulation;

import com.flowsight.dto.simulation.FinancialBaseline;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import com.flowsight.ml.forecast.ForecastPriors;
import com.flowsight.ml.forecast.MonthlyHistory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FlexibilityAndVolatilityTest {

    private static FinancialBaseline baseline(String volatility) {
        return FinancialBaseline.builder()
            .monthlyIncome(new BigDecimal("100000")).monthlySpend(new BigDecimal("80000"))
            .monthlyRecurring(new BigDecimal("25000")).monthlyDiscretionary(new BigDecimal("20000"))
            .monthlyNetSavings(new BigDecimal("20000")).dataMonths(3).hasEnoughData(true)
            .monthlyNetVolatility(volatility == null ? null : new BigDecimal(volatility))
            .build();
    }

    @Test
    void theBufferMeasuresSwingsNotSavingsTwice() {
        FlexibilityCalculator calc = new FlexibilityCalculator();
        // recurring .5 * .35 + savings (20% / 30%) * .35 = 0.4083; buffer adds up to .30
        assertThat(calc.scoreFrom(baseline("5000"))).isEqualTo(71);    // 20,000 covers 2 sd of 5,000
        assertThat(calc.scoreFrom(baseline("20000"))).isEqualTo(56);   // covers half: .15
        assertThat(calc.scoreFrom(baseline("0"))).isEqualTo(71);
        assertThat(calc.scoreFrom(baseline(null))).isEqualTo(71);      // unknown swings: 20% uncommitted income
    }

    private static MonthlyHistory history(double... spend) {
        LocalDate first = LocalDate.now().withDayOfMonth(1).minusMonths(spend.length);
        List<MonthlyHistory.Txn> txns = new ArrayList<>();
        for (int i = 0; i < spend.length; i++) {
            txns.add(new MonthlyHistory.Txn(first.plusMonths(i), TransactionType.CREDIT, TransactionCategory.INCOME, 50000));
            txns.add(new MonthlyHistory.Txn(first.plusMonths(i), TransactionType.DEBIT, TransactionCategory.OTHER, spend[i]));
        }
        return MonthlyHistory.of(txns, first, LocalDate.now());
    }

    @Test
    void volatilityIsTheUsersOwnSwingShrunkTowardThePrior() {
        ForecastPriors priors = ForecastPriors.bundled();
        CashFlowVolatility v = CashFlowVolatility.of(history(30000, 40000, 35000, 30000, 40000, 35000), priors);
        assertThat(v.months()).isEqualTo(6);
        assertThat(v.ownSd()).isCloseTo(Math.sqrt(20e6), within(1e-6));
        assertThat(v.ownWeight()).isCloseTo(0.5, within(1e-9));
        assertThat(v.sd()).isCloseTo(Math.sqrt((6 * 20e6 + 6 * v.priorSd() * v.priorSd()) / 12), within(1e-6));
        assertThat(v.source()).isEqualTo("SHRUNK_TO_PRIOR");
        double scale = v.sd() / v.ownSd();
        double[] expected = {5000 * scale, -5000 * scale, 0, 5000 * scale, -5000 * scale, 0};
        for (int i = 0; i < expected.length; i++) assertThat(v.residuals()[i]).isCloseTo(expected[i], within(1e-6));

        CashFlowVolatility one = CashFlowVolatility.of(history(30000), priors);
        assertThat(one.ownSd()).isNull();
        assertThat(one.sd()).isEqualTo(one.priorSd());
        assertThat(one.residuals()).isEmpty();
        assertThat(one.source()).isEqualTo("PRIOR");
    }
}
