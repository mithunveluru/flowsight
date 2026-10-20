package com.flowsight.ml.forecast;

import com.flowsight.entity.Transaction;
import com.flowsight.ml.MlCapability;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.MlPrediction;
import com.flowsight.ml.PredictionContext;
import com.flowsight.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A user's cash-flow forecast, computed on request from their last 24 complete months. Served by
 * ml-service's forecaster when that capability is enabled and answers in time, otherwise in-app
 * with the same method and priors.
 */
@Service
public class ForecastService {

    private final TransactionRepository transactions;
    private final MlClient              ml;
    private final ForecastPriors        priors;

    @Autowired
    public ForecastService(TransactionRepository transactions, MlClient ml) {
        this(transactions, ml, ForecastPriors.bundled());
    }

    ForecastService(TransactionRepository transactions, MlClient ml, ForecastPriors priors) {
        this.transactions = transactions;
        this.ml = ml;
        this.priors = priors;
    }

    public MonthlyHistory history(UUID userId, LocalDate today) {
        LocalDate from = today.withDayOfMonth(1).minusMonths(MonthlyHistory.MAX_MONTHS);
        List<MonthlyHistory.Txn> rows = transactions.findForExport(userId, null, from, today).stream()
            .filter(t -> t.getAmount() != null && t.getTransactionDate() != null && t.getType() != null)
            .map(ForecastService::txn)
            .toList();
        LocalDate earliest = transactions.findEarliestTransactionDate(userId).orElse(null);
        return MonthlyHistory.of(rows, earliest, today);
    }

    /** Forecast for the current month and the following {@code steps - 1} months. */
    public CashFlowForecast forecast(UUID userId, int steps) {
        return forecast(userId, history(userId, LocalDate.now()), steps);
    }

    public CashFlowForecast forecast(UUID userId, MonthlyHistory h, int steps) {
        if (!ml.isActive(MlCapability.FORECASTER)) return CashFlowForecaster.forecast(h, steps, priors);
        ForecasterRequest request = ForecasterRequest.of(h, steps);
        MlPrediction<ForecasterResponse> p = ml.predict(MlCapability.FORECASTER, request, ForecasterResponse.class,
            PredictionContext.forUser(userId), () -> null);
        CashFlowForecast served = p.fromModel() && p.value() != null
            ? p.value().toForecast(request, h, p.modelVersion())
            : null;
        return served != null ? served : CashFlowForecaster.forecast(h, steps, priors);
    }

    private static MonthlyHistory.Txn txn(Transaction t) {
        return new MonthlyHistory.Txn(t.getTransactionDate(), t.getType(), t.getCategory(), t.getAmount().doubleValue());
    }
}
