package com.flowsight.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.exception.FlowsightException;
import com.flowsight.security.RateLimiter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class MerchantLlmNamerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> answer = new AtomicReference<>("unknown");
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            hits.incrementAndGet();
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String content = JSON.writeValueAsString(java.util.Map.of("merchant", answer.get()));
            byte[] body = JSON.writeValueAsBytes(java.util.Map.of(
                "choices", List.of(java.util.Map.of("message", java.util.Map.of("content", content)))));
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private MerchantLlmNamer namer(boolean enabled, String model) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/chat";
        return new MerchantLlmNamer(enabled, url, model, "test-key", 2000, JSON, rateLimiter);
    }

    @Test
    void acceptsANameCopiedFromTheDescriptor() {
        answer.set("Moonlight Grill");

        assertThat(namer(true, "m").name(userId, "POS MOONLIGHT GRILL 4411", "Moonlight Grill Jbr"))
            .contains("Moonlight Grill");
    }

    @Test
    void rejectsInventedNamesAndNumbers() {
        MerchantLlmNamer namer = namer(true, "m");

        answer.set("Moonlight Restaurant Group");
        assertThat(namer.name(userId, "POS MOONLIGHT GRILL", "Moonlight Grill")).isEmpty();

        answer.set("Grill 24");
        assertThat(namer.name(userId, "POS GRILL STATION", "Grill Station")).isEmpty();

        answer.set("unknown");
        assertThat(namer.name(userId, "POS ZXQV TRADING", "Zxqv Trading")).isEmpty();
    }

    @Test
    void sendsNoIdentifiersOrNumbers() {
        answer.set("Moonlight Grill");

        namer(true, "m").name(userId, "x", "Moonlight Grill a.b@example.com 98765 4111X1234 Card");

        assertThat(bodies).singleElement().satisfies(b -> {
            assertThat(b).contains("Moonlight Grill").doesNotContain("example.com").doesNotContain("98765")
                .doesNotContain("4111");
        });
    }

    @Test
    void personToPersonTransfersAreNeverSent() {
        assertThat(namer(true, "m").name(userId, "UPI/DR/1234/RAHUL SHARMA/HDFC/r@ok/Payment", "Rahul Sharma")).isEmpty();
        assertThat(namer(true, "m").name(userId, "IMPS-1234-PRIYA K", "Priya K")).isEmpty();
        assertThat(hits.get()).isZero();
    }

    @Test
    void offWithoutTheFlagOrAModel() {
        assertThat(namer(false, "m").name(userId, "POS MOONLIGHT GRILL", "Moonlight Grill")).isEmpty();
        assertThat(namer(true, "").name(userId, "POS MOONLIGHT GRILL", "Moonlight Grill")).isEmpty();
        assertThat(MerchantLlmNamer.disabled().active()).isFalse();
        assertThat(hits.get()).isZero();
    }

    @Test
    void failureFallsBackAndBacksOff() {
        status.set(500);
        MerchantLlmNamer namer = namer(true, "m");

        assertThat(namer.name(userId, "POS MOONLIGHT GRILL", "Moonlight Grill")).isEmpty();
        assertThat(namer.name(userId, "POS SUNRISE CAFE", "Sunrise Cafe")).isEmpty();
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void rateLimitedUsersFallBackWithoutACall() {
        doThrow(new FlowsightException("slow down", HttpStatus.TOO_MANY_REQUESTS))
            .when(rateLimiter).check(anyString(), anyInt(), any());

        assertThat(namer(true, "m").name(userId, "POS MOONLIGHT GRILL", "Moonlight Grill")).isEmpty();
        assertThat(hits.get()).isZero();
    }

    @Test
    void repeatedDescriptorsAreAnsweredFromCache() {
        answer.set("Moonlight Grill");
        MerchantLlmNamer namer = namer(true, "m");

        namer.name(userId, "POS MOONLIGHT GRILL", "Moonlight Grill");
        namer.name(userId, "POS MOONLIGHT GRILL", "Moonlight Grill");

        assertThat(hits.get()).isEqualTo(1);
    }
}
