package com.flowsight.insights;

import com.flowsight.analytics.BehavioralAnalysisService;
import com.flowsight.analytics.RecommendationEngine;
import com.flowsight.dto.insights.Recommendation;
import com.flowsight.entity.User;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.ml.FeedbackCorrection;
import com.flowsight.ml.FeedbackService;
import com.flowsight.ml.FeedbackSource;
import com.flowsight.ml.PersonalizationService;
import com.flowsight.ml.SubjectType;
import com.flowsight.ml.UserFeedback;
import com.flowsight.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Accept/dismiss for a recommendation. The id is resolved against the user's own current
 * recommendations (ids embed the user id), so a foreign or stale id is a 404.
 */
@Service
@RequiredArgsConstructor
public class RecommendationFeedbackService {

    public static final String AUDIT_ACTION = "RECOMMENDATION_FEEDBACK";

    public record FeedbackResult(String recommendationId, String kind, String action) {
    }

    private final BehavioralAnalysisService     behavioralAnalysis;
    private final RecommendationEngine          engine;
    private final RecommendationRanker          ranker;
    private final FeedbackService               feedbackService;
    private final PersonalizationService        personalization;
    private final RecommendationStatsRepository stats;
    private final AuditLogService               auditLog;

    @Transactional
    public FeedbackResult record(User user, UUID recommendationId, boolean accepted) {
        UUID userId = user.getId();
        String id = recommendationId.toString();
        Recommendation rec = engine.candidates(userId, behavioralAnalysis.analyze(userId).getPatterns()).stream()
            .filter(r -> id.equals(r.getId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Recommendation", recommendationId));
        String action = accepted ? RecommendationRanker.ACCEPTED : RecommendationRanker.DISMISSED;

        UserFeedback previous = ranker.latestByRecommendation(userId).get(id);
        if (previous != null && action.equals(previous.getCorrectedValue())) {
            return new FeedbackResult(id, rec.getKind(), action);
        }

        double rate = ranker.acceptanceRate(userId, rec.getKind());
        feedbackService.record(userId, new FeedbackCorrection(
            SubjectType.RECOMMENDATION, recommendationId, RecommendationRanker.FIELD, rec.getKind(), action,
            FeedbackSource.RECOMMENDATION_FEEDBACK, RecommendationRanker.MODEL_VERSION, rate));

        if (personalization.isEnabled(userId)) {
            int undoAccepted = previous != null && RecommendationRanker.ACCEPTED.equals(previous.getCorrectedValue()) ? -1 : 0;
            int undoDismissed = previous != null && RecommendationRanker.DISMISSED.equals(previous.getCorrectedValue()) ? -1 : 0;
            stats.adjust(rec.getKind(), (accepted ? 1 : 0) + undoAccepted, (accepted ? 0 : 1) + undoDismissed);
        }
        auditLog.log(user, AUDIT_ACTION, "Recommendation", id, Map.of("kind", rec.getKind(), "action", action));
        return new FeedbackResult(id, rec.getKind(), action);
    }
}
