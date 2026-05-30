package com.flowsight.repository;

import com.flowsight.entity.FinancialGoal;
import com.flowsight.entity.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialGoalRepository extends JpaRepository<FinancialGoal, UUID> {

    List<FinancialGoal> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<FinancialGoal> findByUserIdAndStatusOrderByTargetDateAsc(UUID userId, GoalStatus status);

    Optional<FinancialGoal> findByIdAndUserId(UUID id, UUID userId);
}
