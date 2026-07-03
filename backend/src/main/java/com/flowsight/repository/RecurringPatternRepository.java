package com.flowsight.repository;

import com.flowsight.entity.RecurringPattern;
import com.flowsight.entity.RecurringPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringPatternRepository extends JpaRepository<RecurringPattern, UUID> {

    List<RecurringPattern> findByUserIdOrderByConfidenceDesc(UUID userId);

    List<RecurringPattern> findByUserIdAndIsDismissedFalseOrderByEstimatedAmountDesc(UUID userId);

    Optional<RecurringPattern> findByIdAndUserId(UUID id, UUID userId);

    Optional<RecurringPattern> findByUserIdAndNormalizedKeyAndPeriod(
        UUID userId, String normalizedKey, RecurringPeriod period);

    @Modifying
    @Query("DELETE FROM RecurringPattern r WHERE r.user.id = :userId AND r.isDismissed = false")
    void deleteActiveByUserId(@Param("userId") UUID userId);

    // Deletes auto-detected patterns, preserving user-confirmed and dismissed ones.
    @Modifying
    @Query("""
        DELETE FROM RecurringPattern r
        WHERE r.user.id = :userId
          AND r.isDismissed = false
          AND r.isUserConfirmed = false
        """)
    void deleteActiveNonConfirmedByUserId(@Param("userId") UUID userId);

    @Query("SELECT r FROM RecurringPattern r WHERE r.user.id = :userId AND r.isDismissed = true")
    List<RecurringPattern> findDismissedByUserId(@Param("userId") UUID userId);
}
