package com.flowsight.assistant;

import java.util.Map;

public record Citation(String id, String tool, String title, Map<String, Object> facts) {
}
