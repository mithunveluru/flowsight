package com.flowsight.repository;

import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId
          AND (:category IS NULL OR t.category = :category)
          AND (:startDate IS NULL OR t.transactionDate >= :startDate)
          AND (:endDate   IS NULL OR t.transactionDate <= :endDate)
        ORDER BY t.transactionDate DESC, t.createdAt DESC
        """)
    Page<Transaction> findWithFilters(
        @Param("userId")    UUID userId,
        @Param("category")  TransactionCategory category,
        @Param("startDate") LocalDate startDate,
        @Param("endDate")   LocalDate endDate,
        Pageable pageable
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type     = :type
          AND t.transactionDate >= :from
          AND t.transactionDate <= :to
        """)
    BigDecimal sumByTypeAndDateRange(
        @Param("userId") UUID userId,
        @Param("type")   TransactionType type,
        @Param("from")   LocalDate from,
        @Param("to")     LocalDate to
    );

    long countByUserId(UUID userId);

    Optional<Transaction> findTopByReceiptId(UUID receiptId);

    // -------------------------------------------------------------------------
    // Activity-bounds queries — used to direct the UI to non-empty date ranges
    // after an import lands in a month other than the current one.
    // -------------------------------------------------------------------------

    @Query("SELECT MIN(t.transactionDate) FROM Transaction t WHERE t.user.id = :userId")
    Optional<LocalDate> findEarliestTransactionDate(@Param("userId") UUID userId);

    @Query("SELECT MAX(t.transactionDate) FROM Transaction t WHERE t.user.id = :userId")
    Optional<LocalDate> findLatestTransactionDate(@Param("userId") UUID userId);

    // Months with at least one transaction, newest first ("YYYY-MM").
    @Query(value = """
        SELECT TO_CHAR(DATE_TRUNC('month', transaction_date), 'YYYY-MM') AS month
        FROM transactions
        WHERE user_id = :userId
        GROUP BY DATE_TRUNC('month', transaction_date)
        ORDER BY DATE_TRUNC('month', transaction_date) DESC
        """, nativeQuery = true)
    List<String> findMonthsWithActivity(@Param("userId") UUID userId);

    // Analytics queries

    @Query("""
        SELECT COUNT(t)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.transactionDate >= :from
          AND t.transactionDate <= :to
        """)
    long countByUserIdAndDateRange(
        @Param("userId") UUID userId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    // Returns [TransactionCategory, BigDecimal sum, Long count] per category.
    @Query("""
        SELECT t.category, SUM(t.amount), COUNT(t)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type     = :type
          AND t.transactionDate >= :from
          AND t.transactionDate <= :to
          AND t.category IS NOT NULL
        GROUP BY t.category
        ORDER BY SUM(t.amount) DESC
        """)
    List<Object[]> categoryBreakdown(
        @Param("userId") UUID userId,
        @Param("type") TransactionType type,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    // Returns [String month "YYYY-MM", BigDecimal spend, BigDecimal income] per month.
    @Query(value = """
        SELECT
            TO_CHAR(DATE_TRUNC('month', transaction_date), 'YYYY-MM') AS month,
            COALESCE(SUM(CASE WHEN type = 'DEBIT'  THEN amount ELSE 0 END), 0) AS spend,
            COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END), 0) AS income
        FROM transactions
        WHERE user_id = :userId
          AND transaction_date >= :from
          AND transaction_date <= :to
        GROUP BY DATE_TRUNC('month', transaction_date)
        ORDER BY DATE_TRUNC('month', transaction_date)
        """, nativeQuery = true)
    List<Object[]> monthlyTrend(
        @Param("userId") UUID userId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    // Unpaged list for CSV export and tax detection — bounded by date range.
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId
          AND (:category IS NULL OR t.category = :category)
          AND t.transactionDate >= :from
          AND t.transactionDate <= :to
        ORDER BY t.transactionDate DESC, t.createdAt DESC
        """)
    List<Transaction> findForExport(
        @Param("userId") UUID userId,
        @Param("category") TransactionCategory category,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    // Recurring detection query

    // All DEBIT transactions with a non-blank merchant within a lookback window.
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type = com.flowsight.entity.TransactionType.DEBIT
          AND t.transactionDate >= :from
          AND t.merchant IS NOT NULL
        ORDER BY t.merchant ASC, t.transactionDate ASC
        """)
    List<Transaction> findForRecurringDetection(
        @Param("userId") UUID userId,
        @Param("from") LocalDate from
    );

    // Returns [String merchant, BigDecimal total, Long count], paged for top-N.
    @Query(value = """
        SELECT merchant,
               SUM(amount) AS total,
               COUNT(*)    AS cnt
        FROM transactions
        WHERE user_id = :userId
          AND type = 'DEBIT'
          AND merchant IS NOT NULL
          AND merchant <> ''
          AND transaction_date >= :from
          AND transaction_date <= :to
        GROUP BY merchant
        ORDER BY total DESC
        """, nativeQuery = true)
    List<Object[]> topMerchantsRaw(
        @Param("userId") UUID userId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        Pageable pageable
    );
}
