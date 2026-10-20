package com.flowsight.ml.forecast;

import com.flowsight.entity.TransactionCategory;
import com.flowsight.ml.MlCapability;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.MlPrediction;
import com.flowsight.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForecastServiceTest {

    private static final ForecastPriors PRIORS = ForecastPriors.bundled();
    private static final UUID USER = UUID.randomUUID();

    private final MlClient ml = mock(MlClient.class);
    private final ForecastService service = new ForecastService(mock(TransactionRepository.class), ml, PRIORS);

    private static MonthlyHistory history() {
        List<MonthlyHistory.Txn> txns = new ArrayList<>();
        for (int m = 1; m <= 8; m++) {
            txns.add(MonthlyHistoryTest.credit(String.format("2026-%02d-01", m), 60000));
            txns.add(MonthlyHistoryTest.debit(String.format("2026-%02d-10", m), TransactionCategory.GROCERIES, 8000 + 100 * m));
        }
        txns.add(MonthlyHistoryTest.debit("2026-09-02", TransactionCategory.GROCERIES, 1200));
        return MonthlyHistory.of(txns, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-09-05"));
    }

    private static ForecasterResponse answerFor(CashFlowForecast f) {
        List<ForecasterResponse.Series> series = new ArrayList<>();
        for (SeriesForecast s : List.of(f.income(), f.spend(), f.net(), f.categories().get(0))) {
            series.add(new ForecasterResponse.Series(s.name(), s.kind().id(), s.method().id(), s.dataMonths(), s.scale(),
                s.steps().stream().map(x -> new ForecasterResponse.Step(x.k(), x.point() + 1, x.lower80(), x.upper80() + 2,
                    x.lower95(), x.upper95() + 2)).toList(),
                s.backtestPoints(), s.backtestMae(), s.backtestWape(), s.intervalRatio(), s.ownWeight()));
        }
        return new ForecasterResponse("priors-test", series);
    }

    @Test
    void withTheCapabilityOffItForecastsInAppWithoutCallingMlService() {
        when(ml.isActive(MlCapability.FORECASTER)).thenReturn(false);
        CashFlowForecast f = service.forecast(USER, history(), 4);
        assertThat(f.source()).isEqualTo(CashFlowForecast.IN_APP);
        assertThat(f.dataMonths()).isEqualTo(8);
        assertThat(f.categories()).extracting(SeriesForecast::name).containsExactly("GROCERIES");
        verify(ml, never()).predict(any(), any(), any(), any(), any());
    }

    @Test
    void servesTheModelAnswerWhenItMatchesTheRequest() {
        MonthlyHistory h = history();
        CashFlowForecast inApp = CashFlowForecaster.forecast(h, 4, PRIORS);
        when(ml.isActive(MlCapability.FORECASTER)).thenReturn(true);
        when(ml.predict(eq(MlCapability.FORECASTER), any(), eq(ForecasterResponse.class), any(), any()))
            .thenReturn(new MlPrediction<>(answerFor(inApp), MlPrediction.Source.MODEL, "v1", null));

        CashFlowForecast f = service.forecast(USER, h, 4);
        assertThat(f.source()).isEqualTo(CashFlowForecast.ML_SERVICE);
        assertThat(f.modelVersion()).isEqualTo("v1");
        assertThat(f.priorsVersion()).isEqualTo("priors-test");
        assertThat(f.spend().step(2).point()).isEqualTo(inApp.spend().step(2).point() + 1);
    }

    @Test
    void fallsBackInAppWhenMlServiceFailsOrAnswersOutOfShape() {
        MonthlyHistory h = history();
        CashFlowForecast inApp = CashFlowForecaster.forecast(h, 4, PRIORS);
        when(ml.isActive(MlCapability.FORECASTER)).thenReturn(true);
        when(ml.predict(eq(MlCapability.FORECASTER), any(), eq(ForecasterResponse.class), any(), any()))
            .thenReturn(new MlPrediction<>(null, MlPrediction.Source.RULES, null, null));
        assertThat(service.forecast(USER, h, 4).source()).isEqualTo(CashFlowForecast.IN_APP);

        ForecasterResponse good = answerFor(inApp);
        List<ForecasterResponse> broken = List.of(
            new ForecasterResponse("p", good.series().subList(0, 2)),                       // no net, no category
            new ForecasterResponse("p", List.of(good.series().get(0), good.series().get(1), good.series().get(2),
                withStep(good.series().get(3), new ForecasterResponse.Step(1, 5, 9, 1, 0, 10)))), // inverted interval
            new ForecasterResponse("p", List.of(good.series().get(0), good.series().get(1), good.series().get(2),
                withStep(good.series().get(3), new ForecasterResponse.Step(1, Double.NaN, 0, 1, 0, 2)))));
        for (ForecasterResponse r : broken) {
            when(ml.predict(eq(MlCapability.FORECASTER), any(), eq(ForecasterResponse.class), any(), any()))
                .thenReturn(new MlPrediction<>(r, MlPrediction.Source.MODEL, "v1", null));
            CashFlowForecast f = service.forecast(USER, h, 4);
            assertThat(f.source()).isEqualTo(CashFlowForecast.IN_APP);
            assertThat(f.spend().step(2).point()).isEqualTo(inApp.spend().step(2).point());
        }
    }

    private static ForecasterResponse.Series withStep(ForecasterResponse.Series s, ForecasterResponse.Step first) {
        List<ForecasterResponse.Step> steps = new ArrayList<>(s.steps());
        steps.set(0, first);
        return new ForecasterResponse.Series(s.name(), s.kind(), s.method(), s.dataMonths(), s.scale(), steps,
            s.backtestPoints(), s.backtestMae(), s.backtestWape(), s.intervalRatio(), s.ownWeight());
    }

    @Test
    void theRequestCarriesMonthlyTotalsOnly() {
        ForecasterRequest r = ForecasterRequest.of(history(), 4);
        assertThat(r.steps()).isEqualTo(4);
        assertThat(r.series()).extracting(ForecasterRequest.Series::name).containsExactly("income", "spend", "GROCERIES");
        assertThat(r.series().get(2).values()).hasSize(8);
        assertThat(r.series().get(2).monthToDate()).isEqualTo(1200);
    }
}
