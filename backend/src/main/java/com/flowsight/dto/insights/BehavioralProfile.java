package com.flowsight.dto.insights;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BehavioralProfile {
    // Short one-line summary like "Weekend-driven spender" — used as a hero label.
    private String summary;
    private List<BehavioralPattern> patterns;
}
