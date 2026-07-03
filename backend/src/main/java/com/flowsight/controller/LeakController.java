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

    // leaks computed fresh each call; no persistence
    @GetMapping
    public ResponseEntity<LeakDetectionResponse> detect(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(leakDetectionService.detectLeaks(user.getId()));
    }
}
