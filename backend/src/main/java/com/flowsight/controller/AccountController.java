package com.flowsight.controller;

import com.flowsight.dto.account.AccountResponse;
import com.flowsight.dto.account.AuditLogResponse;
import com.flowsight.entity.User;
import com.flowsight.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<AccountResponse> getAccount(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(accountService.getAccount(user.getId()));
    }

    @GetMapping("/audit-log")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLog(
        @AuthenticationPrincipal User user,
        @PageableDefault(size = 50) Pageable pageable
    ) {
        return ResponseEntity.ok(accountService.getAuditLog(user.getId(), pageable));
    }
}
