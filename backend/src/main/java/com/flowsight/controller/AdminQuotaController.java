package com.flowsight.controller;

import com.flowsight.dto.account.ReceiptQuotaInfo;
import com.flowsight.dto.admin.QuotaUpdateRequest;
import com.flowsight.service.ReceiptQuotaService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin endpoints for managing user receipt quotas.
 * Authorization is enforced by the security config (Role.ADMIN required).
 */
@RestController
@RequestMapping("/api/v1/admin/quota")
@RequiredArgsConstructor
public class AdminQuotaController {

    private final ReceiptQuotaService quotaService;

    /** Reset a single user's processed-receipt counter to 0. */
    @PostMapping("/users/{userId}/reset")
    public ResponseEntity<ReceiptQuotaInfo> resetUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(quotaService.resetUsage(userId));
    }

    /**
     * Update a user's quota: set a custom receipt limit and/or toggle unlimited.
     * Pass only the fields you want to change.
     */
    @PatchMapping("/users/{userId}")
    public ResponseEntity<ReceiptQuotaInfo> updateUser(
        @PathVariable UUID userId,
        @Valid @RequestBody QuotaUpdateRequest request
    ) {
        ReceiptQuotaInfo result = null;
        if (request.getReceiptLimit() != null) {
            result = quotaService.setLimit(userId, request.getReceiptLimit());
        }
        if (request.getUnlimited() != null) {
            result = quotaService.setUnlimited(userId, request.getUnlimited());
        }
        if (result == null) {
            // Neither field supplied — return current state
            return ResponseEntity.ok(quotaService.resetUsage(userId));
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Bulk reset every user's processed counter to 0. Intended for a monthly
     * refresh job or admin-triggered amnesty.
     */
    @PostMapping("/bulk-reset")
    public ResponseEntity<BulkResetResponse> bulkReset() {
        int affected = quotaService.bulkResetAll();
        return ResponseEntity.ok(new BulkResetResponse(affected));
    }

    @Data
    public static class BulkResetResponse {
        private final int affected;
    }
}
