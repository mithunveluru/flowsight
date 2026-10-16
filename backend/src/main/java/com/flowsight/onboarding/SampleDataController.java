package com.flowsight.onboarding;

import com.flowsight.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding/sample-data")
@RequiredArgsConstructor
public class SampleDataController {

    private final SampleDataService sampleDataService;

    @GetMapping
    public ResponseEntity<SampleDataStatus> status(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(sampleDataService.status(user.getId()));
    }

    // idempotent: a second call returns the existing sample set
    @PostMapping
    public ResponseEntity<SampleDataStatus> load(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(sampleDataService.load(user.getId()));
    }

    @DeleteMapping
    public ResponseEntity<SampleDataStatus> remove(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(sampleDataService.remove(user.getId()));
    }
}
