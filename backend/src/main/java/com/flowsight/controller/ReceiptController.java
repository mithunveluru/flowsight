package com.flowsight.controller;

import com.flowsight.dto.receipt.ReceiptConfirmRequest;
import com.flowsight.dto.receipt.ReceiptResponse;
import com.flowsight.entity.User;
import com.flowsight.service.ReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptResponse> upload(
        @RequestParam("file") MultipartFile file,
        @AuthenticationPrincipal User user
    ) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(receiptService.processReceipt(file, user));
    }

    @GetMapping
    public ResponseEntity<Page<ReceiptResponse>> list(
        @AuthenticationPrincipal User user,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(receiptService.list(user.getId(), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReceiptResponse> getById(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(receiptService.getById(id, user.getId()));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ReceiptResponse> confirm(
        @PathVariable UUID id,
        @Valid @RequestBody ReceiptConfirmRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(receiptService.confirmReceipt(id, request, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        receiptService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
