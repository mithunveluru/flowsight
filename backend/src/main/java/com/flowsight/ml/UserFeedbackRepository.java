package com.flowsight.ml;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, UUID> {

    List<UserFeedback> findByUserIdAndSubjectTypeAndFieldOrderByCreatedAtDesc(
        UUID userId, SubjectType subjectType, String field, Pageable pageable);
}
