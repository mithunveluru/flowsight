package com.flowsight.dto.insights;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InsightNarrative {
    private String text;
    private String source;          // LLM | TEMPLATE
}
