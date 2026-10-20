package com.flowsight.llm;

import java.util.Map;

/** A function the model may call; parameters is a JSON schema object. */
public record ToolSpec(String name, String description, Map<String, Object> parameters) {
}
