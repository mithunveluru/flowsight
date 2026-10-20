package com.flowsight.assistant;

import java.util.List;

/**
 * answered=false means the assistant could not answer (unsupported or write request).
 * mode is LLM or RULES. LLM answers carry inline markers like [t1] that match citation ids.
 */
public record AssistantAnswer(
    String answer,
    boolean answered,
    String mode,
    String intent,
    List<Citation> citations,
    String fallbackReason
) {
}
