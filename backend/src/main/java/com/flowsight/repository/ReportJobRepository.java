package com.flowsight.repository;

import com.flowsight.entity.ReportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportJobRepository extends JpaRepository<ReportJob, UUID> {

    Page<ReportJob> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<ReportJob> findByIdAndUserId(UUID id, UUID userId);
}
