package com.flowsight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.entity.AuditLog;
import com.flowsight.entity.User;
import com.flowsight.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit log for security and compliance.
 *
 * <p>All write methods use {@code Propagation.REQUIRES_NEW} so that audit-log
 * failures cannot roll back the parent business transaction — a failure to log
 * an action should not prevent the action itself.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    public static final String ACTION_USER_REGISTERED      = "USER_REGISTERED";
    public static final String ACTION_USER_LOGIN           = "USER_LOGIN";
    public static final String ACTION_USER_LOGIN_FAILED    = "USER_LOGIN_FAILED";
    public static final String ACTION_TRANSACTION_CREATED  = "TRANSACTION_CREATED";
    public static final String ACTION_TRANSACTION_DELETED  = "TRANSACTION_DELETED";
    public static final String ACTION_CSV_IMPORTED         = "CSV_IMPORTED";
    public static final String ACTION_RECEIPT_UPLOADED     = "RECEIPT_UPLOADED";
    public static final String ACTION_RECEIPT_CONFIRMED    = "RECEIPT_CONFIRMED";
    public static final String ACTION_BUDGET_CREATED       = "BUDGET_CREATED";
    public static final String ACTION_GOAL_CREATED         = "GOAL_CREATED";
    public static final String ACTION_PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String ACTION_PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper       objectMapper;

    /** Optional Spring request scope — only available inside an HTTP request thread. */
    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String action) {
        log(user, action, null, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String action, String resourceType, String resourceId) {
        log(user, action, resourceType, resourceId, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
        User user, String action, String resourceType, String resourceId, Map<String, Object> metadata
    ) {
        try {
            String metadataJson = metadata != null && !metadata.isEmpty()
                ? objectMapper.writeValueAsString(metadata)
                : null;

            AuditLog entry = AuditLog.builder()
                .user(user)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .ipAddress(extractIpAddress())
                .userAgent(extractUserAgent())
                .metadata(metadataJson)
                .build();

            auditLogRepository.save(entry);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit metadata for action {}: {}", action, e.getMessage());
        } catch (Exception e) {
            // Logging an audit event must never break the parent operation
            log.warn("Audit log persist failed for action {}: {}", action, e.getMessage());
        }
    }

    /** Logs a failed login attempt where we may have an email but not a user. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailedLogin(String email) {
        log(null, ACTION_USER_LOGIN_FAILED, "User", null, Map.of("email", email));
    }

    // Request context helpers — safe to call outside an HTTP request

    private String extractIpAddress() {
        if (currentRequest == null) return null;
        try {
            String forwarded = currentRequest.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // The first IP in the chain is the original client
                return forwarded.split(",")[0].trim();
            }
            return currentRequest.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUserAgent() {
        if (currentRequest == null) return null;
        try {
            String ua = currentRequest.getHeader("User-Agent");
            if (ua == null) return null;
            return ua.length() > 500 ? ua.substring(0, 500) : ua;
        } catch (Exception e) {
            return null;
        }
    }

    // Query helpers

    public org.springframework.data.domain.Page<AuditLog> list(
        UUID userId, org.springframework.data.domain.Pageable pageable
    ) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}
