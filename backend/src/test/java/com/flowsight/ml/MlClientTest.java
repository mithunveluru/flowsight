package com.flowsight.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MlClientTest {

    record Label(String category) { }

    record Request(String merchant) { }

    private static final String OK_BODY = """
        {"request_id":"r1","capability":"categorizer","model_version":"cat-1","confidence":0.82,
         "summary":{"category":"GROCERIES"},"result":{"category":"GROCERIES"}}
        """;

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<Responder> responder = new AtomicReference<>();
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();

    private final PredictionLogService predictionLog = mock(PredictionLogService.class);
    private final MutableClock clock = new MutableClock();
    private final UUID userId = UUID.randomUUID();

    interface Responder {
        Object[] respond() throws Exception;
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            authHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            paths.add(exchange.getRequestURI().getPath());
            try {
                Object[] reply = responder.get().respond();
                byte[] bytes = ((String) reply[1]).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders((int) reply[0], bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        respondWith(200, OK_BODY);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void blankUrlServesRulesWithoutCallingOrLogging() {
        MlClient client = client(props("", true, false, 2));

        MlPrediction<Label> p = predict(client);

        assertThat(p.value().category()).isEqualTo("RULES");
        assertThat(p.fromModel()).isFalse();
        assertThat(client.isActive(MlCapability.CATEGORIZER)).isFalse();
        assertThat(hits).hasValue(0);
        verifyNoInteractions(predictionLog);
    }

    @Test
    void missingTokenCountsAsUnconfigured() {
        MlProperties p = new MlProperties(url(), " ", Duration.ofMillis(250), 2, Duration.ofSeconds(30),
            Map.of("categorizer", new MlProperties.CapabilityFlags(true, false, Duration.ofMillis(400))));

        assertThat(predict(client(p)).fromModel()).isFalse();
        assertThat(hits).hasValue(0);
    }

    @Test
    void flagsOffServeRulesWithoutCalling() {
        MlClient client = client(props(url(), false, false, 2));

        assertThat(predict(client).value().category()).isEqualTo("RULES");
        assertThat(client.isActive(MlCapability.CATEGORIZER)).isFalse();
        assertThat(hits).hasValue(0);
        verifyNoInteractions(predictionLog);
    }

    @Test
    void unlistedCapabilityIsOff() {
        MlClient client = client(props(url(), true, false, 2));

        MlPrediction<Label> p = client.predict(MlCapability.FORECASTER, new Request("x"), Label.class,
            PredictionContext.NONE, () -> new Label("RULES"));

        assertThat(p.fromModel()).isFalse();
        assertThat(hits).hasValue(0);
    }

    @Test
    void enabledCapabilityServesModelAndLogsIt() {
        MlClient client = client(props(url() + "/", true, false, 2));
        UUID txId = UUID.randomUUID();

        MlPrediction<Label> p = client.predict(MlCapability.CATEGORIZER, new Request("Fresh Mart"), Label.class,
            PredictionContext.of(userId, SubjectType.TRANSACTION, txId), () -> {
                throw new AssertionError("rules must not run when the model answers");
            });

        assertThat(p.fromModel()).isTrue();
        assertThat(p.value().category()).isEqualTo("GROCERIES");
        assertThat(p.modelVersion()).isEqualTo("cat-1");
        assertThat(p.confidence()).isEqualTo(0.82);
        assertThat(paths).containsExactly("/v1/capabilities/categorizer/predict");
        assertThat(authHeaders).containsExactly("Bearer secret-token");
        assertThat(bodies).containsExactly("{\"merchant\":\"Fresh Mart\"}");

        PredictionLogEntry entry = loggedEntry();
        assertThat(entry.getMode()).isEqualTo(PredictionLogEntry.Mode.SERVE);
        assertThat(entry.getOutcome()).isEqualTo(MlOutcome.OK);
        assertThat(entry.getUserId()).isEqualTo(userId);
        assertThat(entry.getSubjectType()).isEqualTo(SubjectType.TRANSACTION);
        assertThat(entry.getSubjectId()).isEqualTo(txId);
        assertThat(entry.getModelVersion()).isEqualTo("cat-1");
        assertThat(entry.getConfidence()).isEqualByComparingTo("0.82");
        assertThat(entry.getOutputSummary()).containsEntry("category", "GROCERIES");
    }

    @Test
    void slowServiceTimesOutAndFallsBackQuickly() {
        responder.set(() -> {
            Thread.sleep(1500);
            return new Object[]{200, OK_BODY};
        });
        MlClient client = client(props(url(), true, false, 5));

        long start = System.nanoTime();
        MlPrediction<Label> p = predict(client);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(p.value().category()).isEqualTo("RULES");
        assertThat(elapsedMs).isLessThan(1000);
        assertThat(loggedEntry().getOutcome()).isEqualTo(MlOutcome.TIMEOUT);
    }

    @Test
    void unreachableServiceFallsBack() {
        MlClient client = client(props(url(), true, false, 5));
        server.stop(0);

        assertThat(predict(client).value().category()).isEqualTo("RULES");
        assertThat(loggedEntry().getOutcome()).isEqualTo(MlOutcome.ERROR);
    }

    @Test
    void malformedResponseFallsBack() {
        respondWith(200, "{\"model_version\":\"cat-1\"}");
        MlClient client = client(props(url(), true, false, 5));

        assertThat(predict(client).value().category()).isEqualTo("RULES");
        assertThat(loggedEntry().getOutcome()).isEqualTo(MlOutcome.ERROR);
    }

    @Test
    void clientErrorsFallBackWithoutTrippingTheCircuit() {
        respondWith(404, "{\"detail\":\"capability not enabled\"}");
        MlClient client = client(props(url(), true, false, 2));

        for (int i = 0; i < 4; i++) assertThat(predict(client).fromModel()).isFalse();

        assertThat(hits).hasValue(4);
        assertThat(client.circuitState(MlCapability.CATEGORIZER)).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void circuitOpensAfterConsecutiveFailuresAndStopsCalling() {
        respondWith(500, "{}");
        MlClient client = client(props(url(), true, false, 2));

        predict(client);
        predict(client);
        assertThat(client.circuitState(MlCapability.CATEGORIZER)).isEqualTo(CircuitBreaker.State.OPEN);

        MlPrediction<Label> p = predict(client);

        assertThat(p.value().category()).isEqualTo("RULES");
        assertThat(hits).hasValue(2);
        ArgumentCaptor<PredictionLogEntry> captor = ArgumentCaptor.forClass(PredictionLogEntry.class);
        verify(predictionLog, times(3)).record(captor.capture());
        assertThat(captor.getAllValues()).extracting(PredictionLogEntry::getOutcome)
            .containsExactly(MlOutcome.ERROR, MlOutcome.ERROR, MlOutcome.CIRCUIT_OPEN);
    }

    @Test
    void halfOpenProbeSuccessClosesTheCircuit() {
        respondWith(500, "{}");
        MlClient client = client(props(url(), true, false, 2));
        predict(client);
        predict(client);

        clock.advance(Duration.ofSeconds(31));
        respondWith(200, OK_BODY);

        assertThat(predict(client).fromModel()).isTrue();
        assertThat(client.circuitState(MlCapability.CATEGORIZER)).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(predict(client).fromModel()).isTrue();
        assertThat(hits).hasValue(4);
    }

    @Test
    void halfOpenProbeFailureReopensImmediately() {
        respondWith(500, "{}");
        MlClient client = client(props(url(), true, false, 2));
        predict(client);
        predict(client);

        clock.advance(Duration.ofSeconds(31));
        assertThat(predict(client).fromModel()).isFalse();
        assertThat(client.circuitState(MlCapability.CATEGORIZER)).isEqualTo(CircuitBreaker.State.OPEN);

        predict(client);
        assertThat(hits).hasValue(3);
    }

    @Test
    void shadowModeServesRulesAndLogsTheModelAnswer() {
        MlClient client = client(props(url(), false, true, 2));

        MlPrediction<Label> p = predict(client);

        assertThat(p.value().category()).isEqualTo("RULES");
        assertThat(p.fromModel()).isFalse();
        assertThat(client.isActive(MlCapability.CATEGORIZER)).isTrue();
        assertThat(hits).hasValue(1);
        PredictionLogEntry entry = loggedEntry();
        assertThat(entry.getMode()).isEqualTo(PredictionLogEntry.Mode.SHADOW);
        assertThat(entry.getOutcome()).isEqualTo(MlOutcome.OK);
        assertThat(entry.getModelVersion()).isEqualTo("cat-1");
    }

    @Test
    void predictionLogFailureDoesNotAffectTheAnswer() {
        doThrow(new IllegalStateException("db down")).when(predictionLog).record(any());
        MlClient client = client(props(url(), true, false, 2));

        MlPrediction<Label> p = predict(client);

        assertThat(p.fromModel()).isTrue();
        assertThat(p.value().category()).isEqualTo("GROCERIES");
    }

    private MlPrediction<Label> predict(MlClient client) {
        return client.predict(MlCapability.CATEGORIZER, new Request("Fresh Mart"), Label.class,
            PredictionContext.forUser(userId), () -> new Label("RULES"));
    }

    private PredictionLogEntry loggedEntry() {
        ArgumentCaptor<PredictionLogEntry> captor = ArgumentCaptor.forClass(PredictionLogEntry.class);
        verify(predictionLog).record(captor.capture());
        return captor.getValue();
    }

    private MlClient client(MlProperties properties) {
        return new MlClient(properties, new ObjectMapper(), predictionLog, clock, Runnable::run);
    }

    private MlProperties props(String url, boolean enabled, boolean shadow, int threshold) {
        return new MlProperties(url, "secret-token", Duration.ofMillis(250), threshold, Duration.ofSeconds(30),
            Map.of("categorizer", new MlProperties.CapabilityFlags(enabled, shadow, Duration.ofMillis(300))));
    }

    private void respondWith(int status, String body) {
        responder.set(() -> new Object[]{status, body});
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
