package com.flowsight.ml;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface PredictionLogRepository extends JpaRepository<PredictionLogEntry, UUID> {

    @Modifying
    @Query("DELETE FROM PredictionLogEntry p WHERE p.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
