package com.flowsight.dto.simulation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GoalImpact {
    private String goalName;
    private int    delayMonths;
    // false when the scenario leaves no savings for the goal (or over 10 years); delayMonths is then 0
    private boolean reachable;
    private String description;
}
