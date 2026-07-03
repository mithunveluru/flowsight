package com.flowsight.dto.simulation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Tradeoff {
    // Short headline e.g. "10-year opportunity cost"
    private String label;
    // Pre-formatted display value e.g. "₹91,473"
    private String value;
    // Optional one-line explanation
    private String description;
}
