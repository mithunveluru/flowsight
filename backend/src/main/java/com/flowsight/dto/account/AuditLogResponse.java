package com.flowsight.dto.account;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AuditLogResponse {
    private UUID    id;
    private String  action;
    private String  resourceType;
    private String  resourceId;
    private String  ipAddress;
    private String  userAgent;
    private String  metadata;     // raw JSON
    private Instant createdAt;
}
