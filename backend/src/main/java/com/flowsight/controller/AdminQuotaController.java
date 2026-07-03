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

// Admin receipt-quota management (ROLE_ADMIN enforced in SecurityConfig).
@RestController
@RequestMapping("/api/v1/admin/quota")
@RequiredArgsConstructor
public class AdminQuotaController {

    private final ReceiptQuotaService quotaService;

    @PostMapping("/users/{userId}/reset")
    public ResponseEntity<ReceiptQuotaInfo> resetUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(quotaService.resetUsage(userId));
    }

    // set custom limit and/or unlimited; pass only the fields to change
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
            // neither field supplied: return current state
            return ResponseEntity.ok(quotaService.resetUsage(userId));
        }
        return ResponseEntity.ok(result);
    }

    // reset every user's counter (monthly refresh job or amnesty)
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
