package com.flowsight.controller;

import com.flowsight.analytics.InsightsService;
import com.flowsight.dto.insights.InsightsResponse;
import com.flowsight.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final InsightsService insightsService;

    // behavioral profile, recommendations, consequences; computed on demand
    @GetMapping
    public ResponseEntity<InsightsResponse> getInsights(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(insightsService.getInsights(user.getId()));
    }
}
