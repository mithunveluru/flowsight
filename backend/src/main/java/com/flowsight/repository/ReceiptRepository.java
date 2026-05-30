package com.flowsight.repository;

import com.flowsight.entity.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    Page<Receipt> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Receipt> findByIdAndUserId(UUID id, UUID userId);

    /** Receipt upload count for a specific calendar month — used for tier-limit checks. */
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
