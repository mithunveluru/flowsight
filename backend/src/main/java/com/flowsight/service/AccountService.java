package com.flowsight.service;

import com.flowsight.dto.account.AccountResponse;
import com.flowsight.dto.account.AuditLogResponse;
import com.flowsight.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final UserService          userService;
    private final ReceiptQuotaService  quotaService;
    private final AuditLogService      auditLogService;

    public AccountResponse getAccount(UUID userId) {
        User user = userService.findById(userId);

        return AccountResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .role(user.getRole().name())
            .createdAt(user.getCreatedAt())
            .receiptQuota(quotaService.getQuota(user))
            .build();
    }

    public Page<AuditLogResponse> getAuditLog(UUID userId, Pageable pageable) {
        return auditLogService.list(userId, pageable).map(log -> AuditLogResponse.builder()
            .id(log.getId())
            .action(log.getAction())
            .resourceType(log.getResourceType())
            .resourceId(log.getResourceId())
            .ipAddress(log.getIpAddress())
            .userAgent(log.getUserAgent())
            .metadata(log.getMetadata())
            .createdAt(log.getCreatedAt())
            .build());
    }
}
