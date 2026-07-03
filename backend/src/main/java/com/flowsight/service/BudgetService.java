package com.flowsight.service;

import com.flowsight.dto.budget.BudgetRequest;
import com.flowsight.dto.budget.BudgetResponse;
import com.flowsight.dto.budget.BudgetSummaryResponse;
import com.flowsight.entity.*;
import com.flowsight.exception.FlowsightException;
import com.flowsight.exception.ResourceNotFoundException;
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
import java.util.*;
import java.util.stream.Collectors;

// Budget CRUD + live tracking; spend is computed fresh from transactions each call.
@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private static final double NEAR_LIMIT_RATIO = 0.80;

    private final BudgetRepository      budgetRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository        userRepository;
    private final AuditLogService       auditLogService;

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

        return new MonthlyContext(spendByCategory, totalSpend, daysElapsed, totalDays, daysRemaining);
    }

    private BudgetResponse toResponse(Budget budget, MonthlyContext ctx) {
        BigDecimal currentSpend = budget.getCategory() != null
            ? ctx.spendByCategory.getOrDefault(budget.getCategory(), BigDecimal.ZERO)
            : ctx.totalSpend;

        BigDecimal limit       = budget.getMonthlyLimit();
        BigDecimal remaining   = limit.subtract(currentSpend).setScale(2, RoundingMode.HALF_UP);
        double percentUsed     = limit.compareTo(BigDecimal.ZERO) > 0
            ? round1(currentSpend.doubleValue() / limit.doubleValue() * 100)
            : 0.0;

        // project month-end at current pace
        BigDecimal projectedTotal = ctx.daysElapsed > 0
            ? currentSpend.multiply(BigDecimal.valueOf(ctx.totalDays))
                .divide(BigDecimal.valueOf(ctx.daysElapsed), 2, RoundingMode.HALF_UP)
            : currentSpend;

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
            .monthlyLimit(limit)
            .rollover(budget.isRollover())
            .isActive(budget.isActive())
            .currentSpend(currentSpend.setScale(2, RoundingMode.HALF_UP))
            .remaining(remaining)
            .percentUsed(percentUsed)
            .projectedTotal(projectedTotal)
            .daysRemaining(ctx.daysRemaining)
            .status(status)
            .build();
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
        int daysRemaining
    ) {}
}
