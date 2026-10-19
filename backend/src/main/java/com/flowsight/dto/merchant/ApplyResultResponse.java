package com.flowsight.dto.merchant;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ApplyResultResponse {
    private UUID ruleId;
    private int updated;
}
