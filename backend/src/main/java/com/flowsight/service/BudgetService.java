package com.flowsight.service;

import com.flowsight.dto.budget.BudgetRequest;
import com.flowsight.dto.budget.BudgetResponse;
import com.flowsight.dto.budget.BudgetSummaryResponse;
import com.flowsight.entity.*;
import com.flowsight.exception.FlowsightException;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.ml.forecast.BudgetPace;
import com.flowsight.ml.forecast.CashFlowForecast;
import com.flowsight.ml.forecast.ForecastEvidence;
import com.flowsight.ml.forecast.ForecastService;
import com.flowsight.ml.forecast.MonthlyHistory;
import com.flowsight.ml.forecast.SeriesForecast;
import com.flowsight.repository.BudgetRepository;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

// Budget CRUD + live tracking; spend is computed fresh from transactions each call.
@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private static final double NEAR_LIMIT_RATIO = 0.80;
    private static final int    ROLLOVER_MONTHS  = 12;

    private final BudgetRepository      budgetRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository        userRepository;
    private final AuditLogService       auditLogService;
    private final ForecastService       forecastService;

    @Transactional
    public BudgetResponse create(BudgetRequest request, UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();

        // one budget per (user, category)
        Optional<Budget> existing = request.getCategory() != null
            ? budgetRepository.findByUserIdAndCategory(userId, request.getCategory())
            : budgetRepository.findByUserIdAndCategoryIsNull(userId);

        if (existing.isPresent()) {
            throw new FlowsightException(
                request.getCategory() != null
                    ? "A budget for " + request.getCategory().getDisplayName() + " already exists"
                    : "An overall budget already exists",
                HttpStatus.CONFLICT
            );
        }

        Budget budget = Budget.builder()
            .user(user)
            .category(request.getCategory())
            .monthlyLimit(request.getMonthlyLimit().setScale(2, RoundingMode.HALF_UP))
            .rollover(request.isRollover())
            .isActive(true)
            .build();

        Budget saved = budgetRepository.save(budget);
        auditLogService.log(user, AuditLogService.ACTION_BUDGET_CREATED, "Budget", saved.getId().toString());
        return toResponse(saved, computeMonthlyContext(userId));
    }

    @Transactional
    public BudgetResponse update(UUID budgetId, BudgetRequest request, UUID userId) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Budget", budgetId));

        budget.setMonthlyLimit(request.getMonthlyLimit().setScale(2, RoundingMode.HALF_UP));
        budget.setRollover(request.isRollover());
        // category is immutable (unique constraint)

        Budget saved = budgetRepository.save(budget);
        return toResponse(saved, computeMonthlyContext(userId));
    }

    @Transactional
    public void delete(UUID budgetId, UUID userId) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Budget", budgetId));
        budgetRepository.delete(budget);
    }

    public List<BudgetResponse> list(UUID userId) {
        MonthlyContext ctx = computeMonthlyContext(userId);
        return budgetRepository.findByUserIdAndIsActiveTrue(userId).stream()
            .map(b -> toResponse(b, ctx))
            .sorted(Comparator.comparing(BudgetResponse::getCategory,
                Comparator.nullsFirst(Comparator.naturalOrder())))
            .collect(Collectors.toList());
    }

    public BudgetSummaryResponse getSummary(UUID userId) {
        List<BudgetResponse> budgets = list(userId);

        BigDecimal totalBudgeted = budgets.stream()
            .filter(b -> b.getCategory() != null)   // overall budget not double-counted
            .map(BudgetResponse::getMonthlyLimit)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSpent = budgets.stream()
            .filter(b -> b.getCategory() != null)
            .map(BudgetResponse::getCurrentSpend)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        double overallPercent = totalBudgeted.compareTo(BigDecimal.ZERO) > 0
            ? round1(totalSpent.doubleValue() / totalBudgeted.doubleValue() * 100)
            : 0.0;

        int overBudgetCount = (int) budgets.stream()
            .filter(b -> "OVER".equals(b.getStatus()))
            .count();

        return BudgetSummaryResponse.builder()
            .totalBudgeted(totalBudgeted)
            .totalSpent(totalSpent)
            .overallPercentUsed(overallPercent)
            .budgetCount(budgets.size())
            .overBudgetCount(overBudgetCount)
            .budgets(budgets)
            .build();
    }

    private MonthlyContext computeMonthlyContext(UUID userId) {
        YearMonth thisMonth = YearMonth.now();
        LocalDate today     = LocalDate.now();
        LocalDate monthStart = thisMonth.atDay(1);
        LocalDate monthEnd   = thisMonth.atEndOfMonth();

        // this month's per-category spend (DEBIT)
        List<Object[]> rows = transactionRepository.categoryBreakdown(
            userId, TransactionType.DEBIT, monthStart, today);

        Map<TransactionCategory, BigDecimal> spendByCategory = new HashMap<>();
        BigDecimal totalSpend = BigDecimal.ZERO;
        for (Object[] row : rows) {
            TransactionCategory cat    = (TransactionCategory) row[0];
            BigDecimal          amount = toBD(row[1]);
            spendByCategory.put(cat, amount);
            totalSpend = totalSpend.add(amount);
        }

        int daysElapsed   = today.getDayOfMonth();
        int totalDays     = monthEnd.getDayOfMonth();
        int daysRemaining = totalDays - daysElapsed;

        MonthlyHistory history = forecastService.history(userId, today);
        CashFlowForecast forecast = forecastService.forecast(userId, history, 1);
        return new MonthlyContext(spendByCategory, totalSpend, daysElapsed, totalDays, daysRemaining, history, forecast);
    }

    private BudgetResponse toResponse(Budget budget, MonthlyContext ctx) {
        BigDecimal currentSpend = budget.getCategory() != null
            ? ctx.spendByCategory.getOrDefault(budget.getCategory(), BigDecimal.ZERO)
            : ctx.totalSpend;

        BigDecimal carried     = budget.isRollover() ? rolloverCarry(budget, ctx.history) : BigDecimal.ZERO;
        BigDecimal limit       = budget.getMonthlyLimit().add(carried).max(BigDecimal.ZERO);
        BigDecimal remaining   = limit.subtract(currentSpend).setScale(2, RoundingMode.HALF_UP);
        double percentUsed     = limit.compareTo(BigDecimal.ZERO) > 0
            ? round1(currentSpend.doubleValue() / limit.doubleValue() * 100)
            : currentSpend.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;

        BudgetPace.Projection pace = pace(budget, ctx, currentSpend.doubleValue());
        BigDecimal projectedTotal = money(pace.point());

        String status;
        if (percentUsed >= 100) {
            status = "OVER";
        } else if (percentUsed >= NEAR_LIMIT_RATIO * 100) {
            status = "NEAR_LIMIT";
        } else if (projectedTotal.compareTo(limit) > 0) {
            status = "PROJECTED_OVER";
        } else {
            status = "ON_TRACK";
        }

        return BudgetResponse.builder()
            .id(budget.getId())
            .category(budget.getCategory() != null ? budget.getCategory().name() : null)
            .categoryDisplayName(budget.getCategory() != null
                ? budget.getCategory().getDisplayName() : "Overall")
            .monthlyLimit(budget.getMonthlyLimit())
            .rolloverAmount(carried.setScale(2, RoundingMode.HALF_UP))
            .effectiveLimit(limit.setScale(2, RoundingMode.HALF_UP))
            .rollover(budget.isRollover())
            .isActive(budget.isActive())
            .currentSpend(currentSpend.setScale(2, RoundingMode.HALF_UP))
            .remaining(remaining)
            .percentUsed(percentUsed)
            .projectedTotal(projectedTotal)
            .daysRemaining(ctx.daysRemaining)
            .status(status)
            .pace(BudgetResponse.Pace.builder()
                .method(pace.method())
                .remainingShare(Math.round(pace.remainingShare() * 1000) / 1000.0)
                .lower80(money(pace.lower80())).upper80(money(pace.upper80()))
                .lower95(money(pace.lower95())).upper95(money(pace.upper95()))
                .evidence(paceEvidence(budget, ctx))
                .build())
            .build();
    }

    // Unspent (or overspent) limit carried from each complete month since the budget was created,
    // at today's limit: past limits are not kept.
    private BigDecimal rolloverCarry(Budget budget, MonthlyHistory history) {
        if (budget.getCreatedAt() == null) return BigDecimal.ZERO;
        YearMonth created = YearMonth.from(budget.getCreatedAt().atZone(ZoneId.systemDefault()));
        YearMonth earliest = YearMonth.from(history.today()).minusMonths(ROLLOVER_MONTHS);
        double[] spend = seriesHistory(budget, history);
        double carry = 0;
        for (int i = 0; i < history.months().size(); i++) {
            YearMonth m = history.months().get(i);
            if (m.isBefore(created) || m.isBefore(earliest)) continue;
            carry += budget.getMonthlyLimit().doubleValue() - spend[i];
        }
        return money(carry);
    }

    private BudgetPace.Projection pace(Budget budget, MonthlyContext ctx, double spentToDate) {
        MonthlyHistory h = ctx.history;
        if (budget.getCategory() == null) {
            return BudgetPace.project(ctx.forecast.spend(), h.spend(), spentToDate, h.spendSameDays(), h.dayFraction());
        }
        return BudgetPace.project(ctx.forecast.category(budget.getCategory().name()), seriesHistory(budget, h),
            spentToDate, h.categoriesSameDays().getOrDefault(budget.getCategory(), 0.0), h.dayFraction());
    }

    private ForecastEvidence paceEvidence(Budget budget, MonthlyContext ctx) {
        SeriesForecast s = budget.getCategory() == null
            ? ctx.forecast.spend()
            : ctx.forecast.category(budget.getCategory().name());
        return s == null ? null : ForecastEvidence.of(ctx.forecast, s, ctx.history);
    }

    private static double[] seriesHistory(Budget budget, MonthlyHistory h) {
        if (budget.getCategory() == null) return h.spend();
        return h.categories().getOrDefault(budget.getCategory(), new double[h.dataMonths()]);
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal toBD(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        try { return new BigDecimal(o.toString()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // pre-computed monthly spend state
    private record MonthlyContext(
        Map<TransactionCategory, BigDecimal> spendByCategory,
        BigDecimal totalSpend,
        int daysElapsed,
        int totalDays,
        int daysRemaining,
        MonthlyHistory history,
        CashFlowForecast forecast
    ) {}
}
