package com.flowsight.controller;

import com.flowsight.dto.transaction.*;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.User;
import com.flowsight.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
        @Valid @RequestBody CreateTransactionRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(transactionService.create(request, user.getId()));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> list(
        @AuthenticationPrincipal User user,
        @RequestParam(required = false) TransactionCategory category,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @PageableDefault(size = 25, sort = "transactionDate") Pageable pageable
    ) {
        return ResponseEntity.ok(
            transactionService.list(user.getId(), category, startDate, endDate, pageable)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(transactionService.getById(id, user.getId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateTransactionRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(transactionService.update(id, user.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        transactionService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BulkImportResult> importCsv(
        @RequestParam("file") MultipartFile file,
        @AuthenticationPrincipal User user
    ) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(transactionService.importCsv(file, user.getId()));
    }
}
