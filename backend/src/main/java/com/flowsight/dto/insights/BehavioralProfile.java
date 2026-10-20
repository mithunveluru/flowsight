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
    // days since the user's first transaction; detectors need different amounts of history
    private Integer historyDays;
    // pattern codes not evaluated because the history is too short
    private List<String> needsMoreHistory;
    private List<CategoryDrift> drifts;
}
