package com.flowsight.dto.leak;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

// Why a leak (or one of its items) was reported: which detector, over which window, on what inputs.
@Data
@Builder
public class LeakEvidence {
    private String     source;       // RULES | MODEL
    private String     method;       // e.g. "rule:last-two-vs-earlier", "change-point:median-mad"
    private LocalDate  windowStart;
    private LocalDate  windowEnd;
    private Map<String, Object> inputs; // thresholds and measured values, by name
    private Double     confidence;   // 0-1; null when the detector gives none
}
