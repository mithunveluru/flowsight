package com.flowsight.repository;

import com.flowsight.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Atomic increment of receipts_processed — safe under concurrent uploads. */
    @Modifying
    @Query("UPDATE User u SET u.receiptsProcessed = u.receiptsProcessed + 1 WHERE u.id = :userId")
    int incrementReceiptsProcessed(@Param("userId") UUID userId);

    /** Reset a user's receipt counter to 0. */
    @Modifying
    @Query("UPDATE User u SET u.receiptsProcessed = 0 WHERE u.id = :userId")
    int resetReceiptsProcessed(@Param("userId") UUID userId);

    /** Bulk reset for periodic refresh — sets every user's counter to 0. */
    @Modifying
    @Query("UPDATE User u SET u.receiptsProcessed = 0")
    int bulkResetReceiptsProcessed();
}
