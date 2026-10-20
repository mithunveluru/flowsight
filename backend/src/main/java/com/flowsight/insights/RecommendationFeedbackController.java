package com.flowsight.insights;

import com.flowsight.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/insights/recommendations")
@RequiredArgsConstructor
public class RecommendationFeedbackController {

    private final RecommendationFeedbackService feedbackService;

    @PostMapping("/{id}/accept")
    public ResponseEntity<RecommendationFeedbackService.FeedbackResult> accept(
        @AuthenticationPrincipal User user, @PathVariable UUID id
    ) {
        return ResponseEntity.ok(feedbackService.record(user, id, true));
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<RecommendationFeedbackService.FeedbackResult> dismiss(
        @AuthenticationPrincipal User user, @PathVariable UUID id
    ) {
        return ResponseEntity.ok(feedbackService.record(user, id, false));
    }
}
