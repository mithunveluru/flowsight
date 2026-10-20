package com.flowsight.repository;

import com.flowsight.entity.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    Page<Receipt> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Receipt> findByIdAndUserId(UUID id, UUID userId);

    // Row lock for confirm: a concurrent double-submit waits here, then sees the first transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Receipt r WHERE r.id = :id AND r.user.id = :userId")
    Optional<Receipt> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
        SELECT r FROM Receipt r
        WHERE r.filePath IS NOT NULL AND r.imageDeletedAt IS NULL AND r.imageExpiresAt <= :now
        ORDER BY r.imageExpiresAt
        """)
    List<Receipt> findExpiredImages(@Param("now") Instant now, Pageable pageable);

    // conditional, so overlapping cleanup runs never claim the same row twice
    @Transactional
    @Modifying
    @Query("""
        UPDATE Receipt r SET r.filePath = NULL, r.imageDeletedAt = :now, r.updatedAt = :now
        WHERE r.id = :id AND r.filePath IS NOT NULL AND r.imageDeletedAt IS NULL AND r.imageExpiresAt <= :now
        """)
    int markImageDeleted(@Param("id") UUID id, @Param("now") Instant now);

    // Receipt upload count for a specific calendar month — used for tier-limit checks.
    @Query(value = """
        SELECT COUNT(*) FROM receipts
        WHERE user_id = :userId
          AND EXTRACT(YEAR  FROM created_at) = :year
          AND EXTRACT(MONTH FROM created_at) = :month
        """, nativeQuery = true)
    int countByUserIdAndCreatedAtMonth(
        @Param("userId") UUID userId,
        @Param("year") int year,
        @Param("month") int month
    );
}
