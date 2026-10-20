package com.flowsight.service;

import com.flowsight.dto.budget.BudgetResponse;
import com.flowsight.entity.Budget;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.forecast.ForecastService;
import com.flowsight.repository.BudgetRepository;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BudgetPaceAndRolloverTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate THIS_MONTH = TODAY.withDayOfMonth(1);

    private final BudgetRepository budgets = mock(BudgetRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final BudgetService service = new BudgetService(budgets, transactions, mock(UserRepository.class),
        mock(AuditLogService.class), new ForecastService(transactions, mock(MlClient.class)));

    private static Transaction groceries(LocalDate date, String amount) {
        return Transaction.builder().transactionDate(date).type(TransactionType.DEBIT)
            .category(TransactionCategory.GROCERIES).amount(new BigDecimal(amount)).build();
    }

    // groceries of 8,000, 9,000 and 12,000 in the last three complete months, all on the 1st
    private void history(String spentThisMonth) {
        List<Transaction> rows = new ArrayList<>(List.of(
            groceries(THIS_MONTH.minusMonths(3), "8000"),
            groceries(THIS_MONTH.minusMonths(2), "9000"),
            groceries(THIS_MONTH.minusMonths(1), "12000")));
        if (spentThisMonth != null) rows.add(groceries(THIS_MONTH, spentThisMonth));
        when(transactions.findForExport(eq(USER), any(), any(), any())).thenReturn(rows);
        when(transactions.findEarliestTransactionDate(USER)).thenReturn(Optional.of(THIS_MONTH.minusMonths(3)));
        List<Object[]> breakdown = new ArrayList<>();
        if (spentThisMonth != null) {
            breakdown.add(new Object[] {TransactionCategory.GROCERIES, new BigDecimal(spentThisMonth), 1L});
        }
        when(transactions.categoryBreakdown(eq(USER), eq(TransactionType.DEBIT), any(), any())).thenReturn(breakdown);
    }

    private BudgetResponse budget(boolean rollover, LocalDate created) {
        Budget b = Budget.builder().id(UUID.randomUUID()).category(TransactionCategory.GROCERIES)
            .monthlyLimit(new BigDecimal("10000")).rollover(rollover).isActive(true)
            .createdAt(created.atStartOfDay(ZoneId.systemDefault()).toInstant()).build();
        when(budgets.findByUserIdAndIsActiveTrue(USER)).thenReturn(List.of(b));
        return service.list(USER).get(0);
    }

    @Test
    void rolloverCarriesWhatWasLeftAndWhatWasOverspentSinceTheBudgetStarted() {
        history("3000");
        BudgetResponse r = budget(true, THIS_MONTH.minusMonths(3));
        // +2,000 + 1,000 - 2,000
        assertThat(r.getRolloverAmount()).isEqualByComparingTo("1000");
        assertThat(r.getEffectiveLimit()).isEqualByComparingTo("11000");
        assertThat(r.getMonthlyLimit()).isEqualByComparingTo("10000");
        assertThat(r.getRemaining()).isEqualByComparingTo("8000");
    }

    @Test
    void rolloverIgnoresMonthsBeforeTheBudgetExistedAndIsOffByDefault() {
        history("3000");
        assertThat(budget(true, THIS_MONTH.minusMonths(1)).getRolloverAmount()).isEqualByComparingTo("-2000");
        BudgetResponse off = budget(false, THIS_MONTH.minusMonths(3));
        assertThat(off.getRolloverAmount()).isEqualByComparingTo("0");
        assertThat(off.getEffectiveLimit()).isEqualByComparingTo("10000");
    }

    @Test
    void paceUsesTheForecastNotTheDaysElapsed() {
        // the user always buys groceries on the 1st: nothing more is expected this month
        history("9500");
        BudgetResponse r = budget(false, THIS_MONTH.minusMonths(3));
        assertThat(r.getPace().getMethod()).isEqualTo("forecast-remaining");
        assertThat(r.getPace().getRemainingShare()).isZero();
        assertThat(r.getProjectedTotal()).isEqualByComparingTo("9500");
        assertThat(r.getPace().getEvidence().dataMonths()).isEqualTo(3);
        assertThat(r.getStatus()).isEqualTo("NEAR_LIMIT");
    }
}
