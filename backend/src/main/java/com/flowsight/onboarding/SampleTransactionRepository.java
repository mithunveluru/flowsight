package com.flowsight.onboarding;

import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionSource;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface SampleTransactionRepository extends Repository<Transaction, UUID> {

    long countByUserIdAndSource(UUID userId, TransactionSource source);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Transaction t WHERE t.user.id = :userId AND t.source = :source")
    int deleteByUserIdAndSource(@Param("userId") UUID userId, @Param("source") TransactionSource source);
}
