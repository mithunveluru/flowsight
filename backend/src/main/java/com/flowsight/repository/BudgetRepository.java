package com.flowsight.repository;

import com.flowsight.entity.Budget;
import com.flowsight.entity.TransactionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByUserIdAndIsActiveTrue(UUID userId);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);

    Optional<Budget> findByUserIdAndCategory(UUID userId, TransactionCategory category);

    /** The single overall budget per user (NULL category). */
    Optional<Budget> findByUserIdAndCategoryIsNull(UUID userId);
}
