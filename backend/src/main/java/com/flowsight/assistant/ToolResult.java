package com.flowsight.assistant;

import com.flowsight.llm.FactSet;

/** One read-only tool run for the authenticated user; facts are all the model or the answer may cite. */
public record ToolResult(String id, String tool, String title, FactSet facts) {
}
