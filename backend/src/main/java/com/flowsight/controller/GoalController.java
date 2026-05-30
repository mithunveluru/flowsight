package com.flowsight.controller;

import com.flowsight.dto.goal.GoalContributionRequest;
import com.flowsight.dto.goal.GoalRequest;
import com.flowsight.dto.goal.GoalResponse;
import com.flowsight.entity.User;
import com.flowsight.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public ResponseEntity<List<GoalResponse>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(goalService.list(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoalResponse> getById(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(goalService.getById(id, user.getId()));
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(
        @Valid @RequestBody GoalRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(goalService.create(request, user.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody GoalRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(goalService.update(id, request, user.getId()));
    }

    /** Adds a contribution amount to the goal's current progress. */
    @PostMapping("/{id}/contribute")
    public ResponseEntity<GoalResponse> contribute(
        @PathVariable UUID id,
        @Valid @RequestBody GoalContributionRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(goalService.contribute(id, request, user.getId()));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<GoalResponse> markComplete(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(goalService.markComplete(id, user.getId()));
    }

    @PatchMapping("/{id}/abandon")
    public ResponseEntity<GoalResponse> abandon(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(goalService.abandon(id, user.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        goalService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
