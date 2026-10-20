package com.flowsight.llm;

/** A function call requested by the model; arguments are the raw JSON string it produced. */
public record ToolCall(String id, String name, String arguments) {
}
