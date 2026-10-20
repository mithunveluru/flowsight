package com.flowsight.analytics;

import com.flowsight.dto.analytics.AnalyticsTrendResponse;
import com.flowsight.dto.analytics.MonthlyTrendPoint;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.forecast.ForecastService;
import com.flowsight.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsTrendForecastTest {

    private static final UUID USER = UUID.randomUUID();

    @Test
    void projectedMonthsCarryIntervalsAndEvidence() {
        TransactionRepository repo = mock(TransactionRepository.class);
        LocalDate first = LocalDate.now().withDayOfMonth(1).minusMonths(8);
        List<Transaction> rows = new ArrayList<>();
        for (int m = 0; m < 8; m++) {
            rows.add(Transaction.builder().transactionDate(first.plusMonths(m)).type(TransactionType.CREDIT)
                .category(TransactionCategory.INCOME).amount(new BigDecimal("70000")).build());
            rows.add(Transaction.builder().transactionDate(first.plusMonths(m).plusDays(9)).type(TransactionType.DEBIT)
                .category(TransactionCategory.GROCERIES).amount(new BigDecimal(40000 + 1500 * (m % 3))).build());
        }
        when(repo.findForExport(eq(USER), any(), any(), any())).thenReturn(rows);
        when(repo.findEarliestTransactionDate(USER)).thenReturn(Optional.of(first));
        when(repo.monthlyTrend(eq(USER), any(), any())).thenReturn(List.of());
        AnalyticsService service = new AnalyticsService(repo, new ForecastService(repo, mock(MlClient.class)));

        AnalyticsTrendResponse r = service.getTrend(USER, 12);

        List<MonthlyTrendPoint> projected = r.getPoints().stream().filter(MonthlyTrendPoint::isProjected).toList();
        assertThat(r.getPoints()).hasSize(15);
        assertThat(projected).hasSize(3);
        assertThat(projected.get(0).getMonth()).isEqualTo(LocalDate.now().plusMonths(1).toString().substring(0, 7));
        for (MonthlyTrendPoint p : projected) {
            assertThat(p.getIncome()).isEqualByComparingTo("70000");
            assertThat(p.getNet()).isEqualByComparingTo(p.getIncome().subtract(p.getSpend()));
            assertThat(p.getRange().spend().lower80()).isLessThanOrEqualTo(p.getSpend());
            assertThat(p.getRange().spend().upper95()).isGreaterThanOrEqualTo(p.getRange().spend().upper80());
        }
        assertThat(r.getPoints().get(0).getRange()).isNull();
        assertThat(r.getForecast().dataMonths()).isEqualTo(8);
        assertThat(r.getForecast().lowHistory()).isFalse();
        assertThat(r.getForecast().source()).isEqualTo("in-app");
        assertThat(r.getForecast().spend().backtestPoints()).isPositive();
        assertThat(r.getForecast().spend().intervalSource()).isEqualTo("GLOBAL_PRIOR_SHRUNK_TO_OWN_ERRORS");
    }
}
