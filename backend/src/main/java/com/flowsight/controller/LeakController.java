package com.flowsight.controller;

import com.flowsight.analytics.LeakDetectionService;
import com.flowsight.dto.leak.LeakDetectionResponse;
import com.flowsight.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leaks")
@RequiredArgsConstructor
public class LeakController {

    private final LeakDetectionService leakDetectionService;

    /**
     * Returns all detected leaks for the user, computed on-demand from existing
     * transactions and Phase 6 recurring patterns. No persistence — every call
     * runs fresh detection so newly added transactions immediately reflect.
     */
    @GetMapping
    public ResponseEntity<LeakDetectionResponse> detect(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(leakDetectionService.detectLeaks(user.getId()));
    }
}
