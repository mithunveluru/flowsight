package com.flowsight.service;

import com.flowsight.dto.account.AccountResponse;
import com.flowsight.dto.account.AuditLogResponse;
import com.flowsight.dto.account.UpdatePreferencesRequest;
import com.flowsight.entity.User;
import com.flowsight.ml.PersonalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    static final String ACTION_PREFERENCES_UPDATED = "PREFERENCES_UPDATED";

    private final UserService          userService;
    private final ReceiptQuotaService  quotaService;
    private final AuditLogService      auditLogService;
    private final PersonalizationService personalizationService;

    public AccountResponse getAccount(UUID userId) {
        User user = userService.findById(userId);

        return AccountResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .role(user.getRole().name())
            .createdAt(user.getCreatedAt())
            .receiptQuota(quotaService.getQuota(user))
            .personalizationEnabled(personalizationService.isEnabled(user.getId()))
            .build();
    }

    public AccountResponse updatePreferences(User user, UpdatePreferencesRequest request) {
        boolean enabled = request.getPersonalizationEnabled();
        personalizationService.setEnabled(user.getId(), enabled);
        auditLogService.log(user, ACTION_PREFERENCES_UPDATED, "User", user.getId().toString(),
            Map.of("personalizationEnabled", enabled));
        return getAccount(user.getId());
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
