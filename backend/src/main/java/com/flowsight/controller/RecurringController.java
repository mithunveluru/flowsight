package com.flowsight.controller;

import com.flowsight.analytics.RecurringDetectionService;
import com.flowsight.dto.recurring.RecurringPatternResponse;
import com.flowsight.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recurring")
@RequiredArgsConstructor
public class RecurringController {

    private final RecurringDetectionService detectionService;

    /**
     * Returns all active (non-dismissed) recurring patterns.
     * Passing {@code ?refresh=true} re-runs detection from transaction history.
     */
    @GetMapping
    public ResponseEntity<List<RecurringPatternResponse>> list(
        @RequestParam(defaultValue = "false") boolean refresh,
        @AuthenticationPrincipal User user
    ) {
        List<RecurringPatternResponse> result = refresh
            ? detectionService.detectAndRefresh(user.getId())
            : detectionService.getStored(user.getId());
        return ResponseEntity.ok(result);
    }

    /** Re-runs detection and refreshes all patterns from transaction history. */
    @PostMapping("/detect")
    public ResponseEntity<List<RecurringPatternResponse>> detect(
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(detectionService.detectAndRefresh(user.getId()));
    }

    /** Dismisses a pattern — hidden from the list until re-detection re-surfaces it. */
    @DeleteMapping("/{id}")
    public ResponseEntity<RecurringPatternResponse> dismiss(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(detectionService.dismiss(id, user.getId()));
    }

    /** Restores a previously dismissed pattern. */
    @PatchMapping("/{id}/restore")
    public ResponseEntity<RecurringPatternResponse> restore(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(detectionService.restore(id, user.getId()));
    }

    /**
     * Confirms a detected pattern as truly recurring.
     * Confirmed patterns are preserved across re-scans even if confidence drops.
     */
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<RecurringPatternResponse> confirm(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(detectionService.confirm(id, user.getId()));
    }

    /** Removes the user-confirmation flag, returning the pattern to auto-detection. */
    @PatchMapping("/{id}/unconfirm")
    public ResponseEntity<RecurringPatternResponse> unconfirm(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(detectionService.unconfirm(id, user.getId()));
    }
}
