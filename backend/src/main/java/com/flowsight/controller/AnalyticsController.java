package com.flowsight.controller;

import com.flowsight.analytics.AnalyticsService;
import com.flowsight.dto.analytics.AnalyticsOverviewResponse;
import com.flowsight.dto.analytics.AnalyticsTrendResponse;
import com.flowsight.entity.User;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Validated
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    // overview for a date range; defaults to the current calendar month
    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverviewResponse> overview(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @AuthenticationPrincipal User user
    ) {
        LocalDate effectiveTo   = to   != null ? to   : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.withDayOfMonth(1);

        return ResponseEntity.ok(
            analyticsService.getOverview(user.getId(), effectiveFrom, effectiveTo));
    }

    // monthly trend for N months plus a 3-month projection; default 12
    @GetMapping("/trend")
    public ResponseEntity<AnalyticsTrendResponse> trend(
        @RequestParam(defaultValue = "12") @Min(1) @Max(60) int months,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(analyticsService.getTrend(user.getId(), months));
    }

    // date range where the user has data; UI uses it to catch out-of-view imports
    @GetMapping("/activity-bounds")
    public ResponseEntity<com.flowsight.dto.analytics.ActivityBoundsResponse> activityBounds(
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(analyticsService.getActivityBounds(user.getId()));
    }
}
