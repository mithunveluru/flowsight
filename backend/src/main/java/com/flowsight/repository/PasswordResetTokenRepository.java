package com.flowsight.repository;

import com.flowsight.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Defense in depth: when a reset succeeds, invalidate every other live token
    // for that user so a leaked second link can no longer be used.
    @Modifying
    @Query("""
        UPDATE PasswordResetToken t
        SET t.consumedAt = :now
        WHERE t.user.id = :userId
          AND t.consumedAt IS NULL
          AND t.expiresAt > :now
        """)
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    // Periodic housekeeping for expired or consumed rows older than the cutoff.
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff OR t.consumedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
