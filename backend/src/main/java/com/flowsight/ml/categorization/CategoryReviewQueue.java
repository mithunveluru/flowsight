package com.flowsight.ml.categorization;

import com.flowsight.entity.Transaction;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Active learning: the user's unreviewed transactions whose category is least certain, so a
 * review screen asks about the rows where an answer teaches the most. Sample rows never appear.
 */
@Component
@RequiredArgsConstructor
public class CategoryReviewQueue {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT     = 100;

    private final EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<Transaction> mostUncertain(UUID userId, int limit) {
        // lowest confidence first; among equals, the smallest gap between the top two alternatives
        return entityManager.createNativeQuery("""
                SELECT t.* FROM transactions t
                WHERE t.user_id = :userId AND NOT t.is_reviewed AND t.source <> 'SAMPLE'
                ORDER BY t.confidence_score ASC NULLS FIRST,
                         COALESCE((t.category_alternatives -> 0 ->> 'probability')::float8
                                - (t.category_alternatives -> 1 ->> 'probability')::float8, 1) ASC,
                         t.transaction_date DESC, t.id
                LIMIT :limit
                """, Transaction.class)
            .setParameter("userId", userId)
            .setParameter("limit", Math.max(1, Math.min(limit, MAX_LIMIT)))
            .getResultList();
    }
}
