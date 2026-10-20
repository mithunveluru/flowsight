package com.flowsight.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> reply = new AtomicReference<>();
    private final AtomicInteger delayMs = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            try {
                Thread.sleep(delayMs.get());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] out = reply.get().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status.get(), out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OpenAiCompatibleProvider provider(String model, Duration timeout) {
        return new OpenAiCompatibleProvider(true, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/",
            "test-key", model, Duration.ofMillis(500), timeout, mapper, Clock.systemUTC());
    }

    @Test
    void sendsModelToolsAndKeyAndParsesToolCalls() throws Exception {
        reply.set("""
            {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
              {"id":"call_1","type":"function","function":{"name":"leaks","arguments":"{}"}}]}}]}""");
        OpenAiCompatibleProvider p = provider("some-model", Duration.ofSeconds(2));

        ChatCompletion c = p.complete(List.of(ChatMessage.system("s"), ChatMessage.user("q")),
            List.of(new ToolSpec("leaks", "d", Map.of("type", "object"))), 100);

        JsonNode body = mapper.readTree(lastBody.get());
        assertThat(lastAuth.get()).isEqualTo("Bearer test-key");
        assertThat(body.path("model").asText()).isEqualTo("some-model");
        assertThat(body.path("temperature").asInt()).isZero();
        assertThat(body.at("/tools/0/function/name").asText()).isEqualTo("leaks");
        assertThat(c.toolCalls()).containsExactly(new ToolCall("call_1", "leaks", "{}"));
        assertThat(c.content()).isNull();
    }

    @Test
    void missingModelIsReportedAndCoolsDown() {
        status.set(404);
        reply.set("{\"error\":{\"code\":\"model_not_found\"}}");
        OpenAiCompatibleProvider p = provider("gone-model", Duration.ofSeconds(2));

        assertThatThrownBy(() -> p.complete(List.of(ChatMessage.user("q")), List.of(), 10))
            .isInstanceOfSatisfying(LlmException.class,
                e -> assertThat(e.reason()).isEqualTo(LlmException.Reason.MODEL_UNAVAILABLE));
        assertThat(p.isAvailable()).isFalse();
    }

    @Test
    void slowResponseTimesOut() {
        delayMs.set(1500);
        reply.set("{\"choices\":[{\"message\":{\"content\":\"late\"}}]}");
        OpenAiCompatibleProvider p = provider("m", Duration.ofMillis(300));

        assertThatThrownBy(() -> p.complete(List.of(ChatMessage.user("q")), List.of(), 10))
            .isInstanceOfSatisfying(LlmException.class,
                e -> assertThat(e.reason()).isEqualTo(LlmException.Reason.TIMEOUT));
    }

    @Test
    void malformedBodyIsAnError() {
        reply.set("not json");
        OpenAiCompatibleProvider p = provider("m", Duration.ofSeconds(2));

        assertThatThrownBy(() -> p.complete(List.of(ChatMessage.user("q")), List.of(), 10))
            .isInstanceOfSatisfying(LlmException.class,
                e -> assertThat(e.reason()).isEqualTo(LlmException.Reason.MALFORMED));
    }

    @Test
    void noModelNameMeansUnavailableAndNothingIsSent() {
        OpenAiCompatibleProvider p = provider("", Duration.ofSeconds(2));

        assertThat(p.isAvailable()).isFalse();
        assertThatThrownBy(() -> p.complete(List.of(ChatMessage.user("q")), List.of(), 10))
            .isInstanceOf(LlmException.class);
        assertThat(lastBody.get()).isNull();
    }

    @Test
    void disabledByDefaultConstructorValues() {
        OpenAiCompatibleProvider p = new OpenAiCompatibleProvider(false, "https://api.groq.com/openai/v1",
            "key", "model", 2000, 8000, mapper);

        assertThat(p.isAvailable()).isFalse();
    }
}
