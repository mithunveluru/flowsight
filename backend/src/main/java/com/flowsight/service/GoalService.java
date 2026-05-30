package com.flowsight.service;

import com.flowsight.dto.goal.GoalContributionRequest;
import com.flowsight.dto.goal.GoalRequest;
import com.flowsight.dto.goal.GoalResponse;
import com.flowsight.entity.FinancialGoal;
import com.flowsight.entity.GoalStatus;
import com.flowsight.entity.User;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.repository.FinancialGoalRepository;
import com.flowsight.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Financial goal CRUD + progress tracking.
 *
 * <p>Pace status logic:
 * <ul>
 *   <li>COMPLETED — currentAmount ≥ targetAmount (auto-completed)</li>
 *   <li>OVERDUE   — past targetDate without completion</li>
 *   <li>AHEAD     — percentComplete > percentElapsed + 5</li>
 *   <li>BEHIND    — percentComplete < percentElapsed - 5</li>
 *   <li>ON_PACE   — within ±5% band of expected progress</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoalService {

    private static final double PACE_TOLERANCE_PCT = 5.0;

    private final FinancialGoalRepository goalRepository;
    private final UserRepository          userRepository;
    private final AuditLogService         auditLogService;

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @Transactional
    public GoalResponse create(GoalRequest request, UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();

        FinancialGoal goal = FinancialGoal.builder()
            .user(user)
            .name(request.getName().trim())
            .targetAmount(request.getTargetAmount().setScale(2, RoundingMode.HALF_UP))
            .currentAmount(request.getCurrentAmount() != null
                ? request.getCurrentAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO)
            .targetDate(request.getTargetDate())
            .icon(request.getIcon())
            .status(GoalStatus.ACTIVE)
            .build();

        FinancialGoal saved = goalRepository.save(goal);
        auditLogService.log(user, AuditLogService.ACTION_GOAL_CREATED, "FinancialGoal", saved.getId().toString());
        return toResponse(saved);
    }

    @Transactional
    public GoalResponse update(UUID goalId, GoalRequest request, UUID userId) {
        FinancialGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("FinancialGoal", goalId));

        goal.setName(request.getName().trim());
        goal.setTargetAmount(request.getTargetAmount().setScale(2, RoundingMode.HALF_UP));
        goal.setTargetDate(request.getTargetDate());
        if (request.getCurrentAmount() != null) {
            goal.setCurrentAmount(request.getCurrentAmount().setScale(2, RoundingMode.HALF_UP));
        }
        goal.setIcon(request.getIcon());

        return toResponse(goalRepository.save(goal));
    }

    /** Adds an amount to the current progress (a "contribution"). */
    @Transactional
    public GoalResponse contribute(UUID goalId, GoalContributionRequest request, UUID userId) {
        FinancialGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("FinancialGoal", goalId));

        BigDecimal newAmount = goal.getCurrentAmount().add(request.getAmount())
            .setScale(2, RoundingMode.HALF_UP);
        goal.setCurrentAmount(newAmount);

        // Auto-mark COMPLETED if target reached
        if (newAmount.compareTo(goal.getTargetAmount()) >= 0 && goal.getStatus() == GoalStatus.ACTIVE) {
            goal.setStatus(GoalStatus.COMPLETED);
        }

        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse markComplete(UUID goalId, UUID userId) {
        FinancialGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("FinancialGoal", goalId));
        goal.setStatus(GoalStatus.COMPLETED);
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse abandon(UUID goalId, UUID userId) {
        FinancialGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("FinancialGoal", goalId));
        goal.setStatus(GoalStatus.ABANDONED);
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public void delete(UUID goalId, UUID userId) {
        FinancialGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("FinancialGoal", goalId));
        goalRepository.delete(goal);
    }

    public List<GoalResponse> list(UUID userId) {
        return goalRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public GoalResponse getById(UUID goalId, UUID userId) {
        FinancialGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("FinancialGoal", goalId));
        return toResponse(goal);
    }

    // -------------------------------------------------------------------------
    // Progress computation
    // -------------------------------------------------------------------------

    private GoalResponse toResponse(FinancialGoal goal) {
        LocalDate today = LocalDate.now();
        BigDecimal target = goal.getTargetAmount();
        BigDecimal current = goal.getCurrentAmount();
        BigDecimal remaining = target.subtract(current).max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);

        double percentComplete = target.compareTo(BigDecimal.ZERO) > 0
            ? Math.min(100.0, round1(current.doubleValue() / target.doubleValue() * 100))
            : 0.0;

        int daysRemaining = (int) ChronoUnit.DAYS.between(today, goal.getTargetDate());

        // Daily pace required to hit the goal on time
        BigDecimal dailyPace = daysRemaining > 0
            ? remaining.divide(BigDecimal.valueOf(daysRemaining), 2, RoundingMode.HALF_UP)
            : remaining;

        String paceStatus = computePaceStatus(goal, percentComplete, today, daysRemaining);

        return GoalResponse.builder()
            .id(goal.getId())
            .name(goal.getName())
            .targetAmount(target)
            .currentAmount(current)
            .remaining(remaining)
            .targetDate(goal.getTargetDate())
            .icon(goal.getIcon())
            .status(goal.getStatus().name())
            .percentComplete(percentComplete)
            .daysRemaining(Math.max(0, daysRemaining))
            .dailyPaceRequired(dailyPace)
            .paceStatus(paceStatus)
            .build();
    }

    private String computePaceStatus(
        FinancialGoal goal, double percentComplete, LocalDate today, int daysRemaining
    ) {
        if (goal.getStatus() == GoalStatus.COMPLETED || percentComplete >= 100) return "COMPLETED";
        if (goal.getStatus() == GoalStatus.ABANDONED)                          return "ABANDONED";
        if (daysRemaining < 0)                                                 return "OVERDUE";

        // createdAt is null when the goal was just created in this same transaction
        // (@CreationTimestamp fires on commit, not save). Treat today as the start.
        LocalDate startDate = goal.getCreatedAt() != null
            ? goal.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            : today;

        long totalDays = ChronoUnit.DAYS.between(startDate, goal.getTargetDate());
        if (totalDays <= 0) return "ON_PACE";

        long daysElapsed = ChronoUnit.DAYS.between(startDate, today);
        double percentElapsed = (double) daysElapsed / totalDays * 100;

        if      (percentComplete > percentElapsed + PACE_TOLERANCE_PCT) return "AHEAD";
        else if (percentComplete < percentElapsed - PACE_TOLERANCE_PCT) return "BEHIND";
        else                                                            return "ON_PACE";
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
