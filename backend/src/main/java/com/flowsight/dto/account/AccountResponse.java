package com.flowsight.dto.account;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AccountResponse {
    private UUID    id;
    private String  email;
    private String  fullName;
    private String  role;
    private Instant createdAt;

    private SubscriptionInfo subscription;
    private UsageInfo        usage;
}
