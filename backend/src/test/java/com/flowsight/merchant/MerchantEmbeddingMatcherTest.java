package com.flowsight.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.MlProperties;
import com.flowsight.ml.PredictionLogService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MerchantEmbeddingMatcherTest {

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> sent = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/capabilities/merchant-embeddings/predict", exchange -> {
            hits.incrementAndGet();
            sent.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private MerchantEmbeddingMatcher matcher(boolean enabled) {
        MlProperties props = new MlProperties("http://127.0.0.1:" + server.getAddress().getPort(), "token",
            Duration.ofMillis(250), 5, Duration.ofSeconds(30),
            Map.of("merchant-embeddings", new MlProperties.CapabilityFlags(enabled, false, Duration.ofMillis(800))));
        return new MerchantEmbeddingMatcher(new MlClient(props, new ObjectMapper(), mock(PredictionLogService.class)), 0.75);
    }

    private static String reply(String canonical, double similarity) {
        return "{\"request_id\":\"r\",\"capability\":\"merchant-embeddings\",\"model_version\":\"v1\","
            + "\"confidence\":" + similarity + ",\"summary\":{\"matched\":true},"
            + "\"result\":{\"canonical\":\"" + canonical + "\",\"alias\":\"kibsons\",\"similarity\":" + similarity + "}}";
    }

    @Test
    void returnsTheNeighbourWhenCloseEnough() {
        body.set(reply("Kibsons", 0.88));

        assertThat(matcher(true).match(UUID.randomUUID(), "Kbsns Fresh"))
            .contains(new MerchantEmbeddingMatcher.Match("Kibsons", 0.88));
        assertThat(sent.get()).isEqualTo("{\"text\":\"Kbsns Fresh\"}");
    }

    @Test
    void ignoresDistantNeighbours() {
        body.set(reply("Kibsons", 0.5));

        assertThat(matcher(true).match(UUID.randomUUID(), "Kbsns Fresh")).isEmpty();
    }

    @Test
    void flagOffSendsNothing() {
        body.set(reply("Kibsons", 0.88));

        assertThat(matcher(false).match(UUID.randomUUID(), "Kbsns Fresh")).isEmpty();
        assertThat(MerchantEmbeddingMatcher.disabled().match(UUID.randomUUID(), "Kbsns Fresh")).isEmpty();
        assertThat(hits.get()).isZero();
    }

    @Test
    void serviceFailureFallsThrough() {
        status.set(503);
        body.set("{\"detail\":\"no active model\"}");

        assertThat(matcher(true).match(UUID.randomUUID(), "Kbsns Fresh")).isEmpty();
        assertThat(hits.get()).isEqualTo(1);
    }
}
