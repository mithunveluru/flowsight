package com.flowsight.controller;

import com.flowsight.dto.budget.BudgetRequest;
import com.flowsight.dto.budget.BudgetResponse;
import com.flowsight.dto.budget.BudgetSummaryResponse;
import com.flowsight.entity.User;
import com.flowsight.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    public ResponseEntity<BudgetSummaryResponse> summary(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(budgetService.getSummary(user.getId()));
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(
        @Valid @RequestBody BudgetRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(budgetService.create(request, user.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BudgetResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody BudgetRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(budgetService.update(id, request, user.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        budgetService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
