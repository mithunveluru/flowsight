package com.flowsight.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Scripted provider for tests: replays completions or exceptions in order and records requests. */
public class StubChatProvider implements ChatProvider {

    private final boolean available;
    private final Deque<Object> script = new ArrayDeque<>();
    public final List<List<ChatMessage>> requests = new ArrayList<>();

    public StubChatProvider(boolean available) {
        this.available = available;
    }

    public StubChatProvider reply(String content) {
        script.add(new ChatCompletion(content, List.of()));
        return this;
    }

    public StubChatProvider callTool(String id, String tool, String arguments) {
        script.add(new ChatCompletion(null, List.of(new ToolCall(id, tool, arguments))));
        return this;
    }

    public StubChatProvider callTools(ToolCall... calls) {
        script.add(new ChatCompletion(null, List.of(calls)));
        return this;
    }

    public StubChatProvider fail(LlmException.Reason reason) {
        script.add(new LlmException(reason, "stubbed"));
        return this;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public ChatCompletion complete(List<ChatMessage> messages, List<ToolSpec> tools, int maxTokens) throws LlmException {
        requests.add(List.copyOf(messages));
        Object next = script.poll();
        if (next == null) throw new LlmException(LlmException.Reason.HTTP_ERROR, "script exhausted");
        if (next instanceof LlmException e) throw e;
        return (ChatCompletion) next;
    }

    public String allSentText() {
        StringBuilder sb = new StringBuilder();
        for (List<ChatMessage> r : requests) for (ChatMessage m : r) sb.append(m.content()).append('\n');
        return sb.toString();
    }
}
