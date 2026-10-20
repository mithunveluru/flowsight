package com.flowsight.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Chat completions over any OpenAI-compatible endpoint (Groq by default). Off unless
 * LLM_ENABLED, a base URL, an API key and LLM_MODEL are all set; no model name is assumed.
 * After an auth, missing-model or rate-limit answer the provider reports itself unavailable
 * for a cool-down so callers go straight to their deterministic path.
 */
@Component
@Slf4j
public class OpenAiCompatibleProvider implements ChatProvider {

    private static final Duration COOLDOWN = Duration.ofMinutes(1);

    private final boolean      enabled;
    private final String       baseUrl;
    private final String       apiKey;
    private final String       model;
    private final Duration     timeout;
    private final ObjectMapper mapper;
    private final Clock        clock;
    private final HttpClient   http;

    private volatile long blockedUntil;

    @Autowired
    public OpenAiCompatibleProvider(
        @Value("${LLM_ENABLED:false}") boolean enabled,
        @Value("${LLM_BASE_URL:https://api.groq.com/openai/v1}") String baseUrl,
        @Value("${LLM_API_KEY:${application.groq.api-key:}}") String apiKey,
        @Value("${LLM_MODEL:}") String model,
        @Value("${LLM_CONNECT_TIMEOUT_MS:2000}") long connectTimeoutMillis,
        @Value("${LLM_TIMEOUT_MS:8000}") long timeoutMillis,
        ObjectMapper mapper
    ) {
        this(enabled, baseUrl, apiKey, model, Duration.ofMillis(Math.max(100, connectTimeoutMillis)),
            Duration.ofMillis(Math.max(100, timeoutMillis)), mapper, Clock.systemUTC());
    }

    OpenAiCompatibleProvider(boolean enabled, String baseUrl, String apiKey, String model,
                             Duration connectTimeout, Duration timeout, ObjectMapper mapper, Clock clock) {
        this.enabled = enabled;
        this.baseUrl = trimSlash(baseUrl);
        this.apiKey  = apiKey == null ? "" : apiKey.trim();
        this.model   = model == null ? "" : model.trim();
        this.timeout = timeout;
        this.mapper  = mapper;
        this.clock   = clock;
        this.http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(connectTimeout)
            .build();
    }

    @Override
    public boolean isAvailable() {
        return enabled && !baseUrl.isEmpty() && !apiKey.isEmpty() && !model.isEmpty()
            && clock.millis() >= blockedUntil;
    }

    @Override
    public ChatCompletion complete(List<ChatMessage> messages, List<ToolSpec> tools, int maxTokens)
        throws LlmException {
        if (!isAvailable()) throw new LlmException(LlmException.Reason.UNAVAILABLE, "provider not configured");
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body(messages, tools, maxTokens))))
                .build();
        } catch (Exception e) {
            throw new LlmException(LlmException.Reason.MALFORMED, "could not build request");
        }

        HttpResponse<String> response;
        try {
            // the request timeout does not bound a slow body; the future's does
            response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new LlmException(LlmException.Reason.TIMEOUT, "timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.Reason.IO, "interrupted");
        } catch (ExecutionException e) {
            throw new LlmException(e.getCause() instanceof java.net.http.HttpTimeoutException
                ? LlmException.Reason.TIMEOUT : LlmException.Reason.IO, "request failed");
        }

        int status = response.statusCode();
        if (status != 200) {
            LlmException.Reason reason = switch (status) {
                case 401, 403, 404 -> LlmException.Reason.MODEL_UNAVAILABLE;
                case 429 -> LlmException.Reason.RATE_LIMITED;
                default -> LlmException.Reason.HTTP_ERROR;
            };
            if (reason != LlmException.Reason.HTTP_ERROR) blockedUntil = clock.millis() + COOLDOWN.toMillis();
            log.warn("LLM call returned HTTP {}", status);
            throw new LlmException(reason, "HTTP " + status);
        }
        return parse(response.body());
    }

    private Map<String, Object> body(List<ChatMessage> messages, List<ToolSpec> tools, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages.stream().map(OpenAiCompatibleProvider::message).toList());
        body.put("temperature", 0);
        body.put("max_tokens", maxTokens);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools.stream().map(t -> Map.of(
                "type", "function",
                "function", Map.of("name", t.name(), "description", t.description(), "parameters", t.parameters())
            )).toList());
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private static Map<String, Object> message(ChatMessage m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", m.role());
        out.put("content", m.content());
        if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            out.put("tool_calls", m.toolCalls().stream().map(c -> Map.of(
                "id", c.id(), "type", "function",
                "function", Map.of("name", c.name(), "arguments", c.arguments() == null ? "{}" : c.arguments())
            )).toList());
        }
        if (m.toolCallId() != null) out.put("tool_call_id", m.toolCallId());
        return out;
    }

    private ChatCompletion parse(String body) throws LlmException {
        try {
            JsonNode message = mapper.readTree(body).at("/choices/0/message");
            if (message.isMissingNode()) throw new LlmException(LlmException.Reason.MALFORMED, "no choices");
            List<ToolCall> calls = new ArrayList<>();
            for (JsonNode call : message.path("tool_calls")) {
                String name = call.at("/function/name").asText("");
                if (name.isEmpty()) continue;
                calls.add(new ToolCall(call.path("id").asText(""), name, call.at("/function/arguments").asText("{}")));
            }
            JsonNode content = message.path("content");
            return new ChatCompletion(content.isTextual() ? content.asText() : null, calls);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException(LlmException.Reason.MALFORMED, "unreadable response");
        }
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }
}
