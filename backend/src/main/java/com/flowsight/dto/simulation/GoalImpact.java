package com.flowsight.dto.simulation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GoalImpact {
    private String goalName;
    private int    delayMonths;
    private String description;
}
