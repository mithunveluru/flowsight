package com.flowsight.llm;

import java.util.List;

/** The first choice of a chat completion: text, tool calls, or both. */
public record ChatCompletion(String content, List<ToolCall> toolCalls) {

    public ChatCompletion {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
