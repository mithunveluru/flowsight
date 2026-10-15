package com.flowsight.ml;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final int VALUE_LENGTH   = 500;
    private static final int FIELD_LENGTH   = 50;
    private static final int VERSION_LENGTH = 64;
    private static final int MAX_HISTORY    = 500;

    private final UserFeedbackRepository repository;
    private final PersonalizationService personalization;

    // Joins the caller's transaction so a correction is stored only if the user's edit is.
    @Transactional
    public UserFeedback record(UUID userId, FeedbackCorrection correction) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(correction.subjectType(), "subjectType");
        Objects.requireNonNull(correction.subjectId(), "subjectId");
        Objects.requireNonNull(correction.source(), "source");
        if (correction.field() == null || correction.field().isBlank()) {
            throw new IllegalArgumentException("field is required");
        }
        return repository.save(UserFeedback.builder()
            .userId(userId)
            .subjectType(correction.subjectType())
            .subjectId(correction.subjectId())
            .field(truncate(correction.field().trim(), FIELD_LENGTH))
            .predictedValue(truncate(correction.predictedValue(), VALUE_LENGTH))
            .correctedValue(truncate(correction.correctedValue(), VALUE_LENGTH))
            .source(correction.source())
            .modelVersion(truncate(correction.modelVersion(), VERSION_LENGTH))
            .confidence(Confidence.of(correction.confidence()))
            .build());
    }

    // The user's own recent corrections for one field, newest first; empty when they opted out.
    @Transactional(readOnly = true)
    public List<UserFeedback> forPersonalization(UUID userId, SubjectType subjectType, String field, int limit) {
        if (!personalization.isEnabled(userId)) return List.of();
        return repository.findByUserIdAndSubjectTypeAndFieldOrderByCreatedAtDesc(
            userId, subjectType, field, PageRequest.of(0, Math.max(1, Math.min(limit, MAX_HISTORY))));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
