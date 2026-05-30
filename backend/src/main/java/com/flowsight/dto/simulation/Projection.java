package com.flowsight.dto.simulation;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Projection {
    private List<ProjectionPoint> before;
    private List<ProjectionPoint> after;
}
