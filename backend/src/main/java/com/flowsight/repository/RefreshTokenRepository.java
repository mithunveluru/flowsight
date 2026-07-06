package com.flowsight.repository;

import com.flowsight.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Theft response and password change: kill every live session for the user.
    @Modifying
    @Query("""
        UPDATE RefreshToken t
        SET t.revokedAt = :now
        WHERE t.user.id = :userId
          AND t.revokedAt IS NULL
          AND t.consumedAt IS NULL
          AND t.expiresAt > :now
        """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    // Periodic housekeeping for expired/consumed/revoked rows older than the cutoff.
    @Modifying
    @Query("""
        DELETE FROM RefreshToken t
        WHERE t.expiresAt < :cutoff OR t.consumedAt < :cutoff OR t.revokedAt < :cutoff
        """)
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
