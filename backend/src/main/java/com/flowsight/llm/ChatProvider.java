package com.flowsight.llm;

import java.util.List;

/** A chat model behind an OpenAI-compatible API. Callers must check isAvailable() and fall back. */
public interface ChatProvider {

    boolean isAvailable();

    ChatCompletion complete(List<ChatMessage> messages, List<ToolSpec> tools, int maxTokens) throws LlmException;
}
