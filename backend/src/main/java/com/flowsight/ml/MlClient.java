package com.flowsight.ml;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Calls ml-service for a capability and always yields an answer: the model's when the
 * capability is enabled and the call succeeds in time, otherwise the caller's rule-based
 * result. Nothing is sent and nothing is logged while ML is unconfigured or flagged off.
 */
@Service
@Slf4j
public class MlClient {

    private static final TypeReference<Map<String, Object>> SUMMARY = new TypeReference<>() { };

    private final MlProperties         properties;
    private final ObjectMapper         objectMapper;
    private final PredictionLogService predictionLog;
    private final Clock                clock;
    private final Executor             shadowExecutor;
    private final HttpClient           http;

    private final Map<MlCapability, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    @Autowired
    public MlClient(MlProperties properties, ObjectMapper objectMapper, PredictionLogService predictionLog) {
        this(properties, objectMapper, predictionLog, Clock.systemUTC(), boundedShadowExecutor());
    }

    MlClient(
        MlProperties properties, ObjectMapper objectMapper, PredictionLogService predictionLog,
        Clock clock, Executor shadowExecutor
    ) {
        this.properties     = properties;
        this.objectMapper   = objectMapper;
        this.predictionLog  = predictionLog;
        this.clock          = clock;
        this.shadowExecutor = shadowExecutor;
        this.http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.connectTimeout())
            .build();
        if (properties.url() != null && !properties.url().isBlank() && !properties.configured()) {
            log.warn("ML_SERVICE_URL is set but ML_SERVICE_TOKEN is empty; ML calls stay disabled");
        }
    }

    // Lets callers skip building an expensive request when the answer will be rules anyway.
    public boolean isActive(MlCapability capability) {
        MlProperties.CapabilityFlags flags = properties.flags(capability);
        return properties.configured() && (flags.enabled() || flags.shadow());
    }

    public <T> MlPrediction<T> predict(
        MlCapability capability, Object input, Class<T> resultType,
        PredictionContext context, Supplier<T> ruleBased
    ) {
        if (!properties.configured()) return MlPrediction.rules(ruleBased.get());
        MlProperties.CapabilityFlags flags = properties.flags(capability);

        if (flags.enabled()) {
            Attempt<T> attempt = call(capability, input, resultType, flags.timeout());
            record(capability, PredictionLogEntry.Mode.SERVE, attempt, context);
            return attempt.outcome() == MlOutcome.OK
                ? MlPrediction.model(attempt.value(), attempt.modelVersion(), attempt.confidence())
                : MlPrediction.rules(ruleBased.get(), attempt.outcome());
        }

        T rules = ruleBased.get();
        if (flags.shadow()) {
            shadowExecutor.execute(() -> record(capability, PredictionLogEntry.Mode.SHADOW,
                call(capability, input, resultType, flags.timeout()), context));
        }
        return MlPrediction.rules(rules);
    }

    CircuitBreaker.State circuitState(MlCapability capability) {
        return breaker(capability).state();
    }

    private <T> Attempt<T> call(MlCapability capability, Object input, Class<T> resultType, Duration timeout) {
        CircuitBreaker breaker = breaker(capability);
        if (!breaker.tryAcquire()) return Attempt.failed(MlOutcome.CIRCUIT_OPEN, 0);

        long started = System.nanoTime();
        CompletableFuture<HttpResponse<byte[]>> future = null;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/v1/capabilities/" + capability.id() + "/predict"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + properties.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(input)))
                .build();
            future = http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
            // Bound the whole exchange, body included; HttpRequest.timeout alone stops at headers.
            HttpResponse<byte[]> response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            if (status >= 500) return fail(capability, breaker, MlOutcome.ERROR, started);
            if (status >= 300) {
                breaker.onSuccess();
                return Attempt.failed(MlOutcome.REJECTED, elapsed(started));
            }
            JsonNode body = objectMapper.readTree(response.body());
            JsonNode result = body.path("result");
            if (!result.isObject()) return fail(capability, breaker, MlOutcome.ERROR, started);
            T value = objectMapper.treeToValue(result, resultType);
            JsonNode confidence = body.path("confidence");
            Map<String, Object> summary = body.path("summary").isObject()
                ? objectMapper.convertValue(body.get("summary"), SUMMARY)
                : null;
            breaker.onSuccess();
            return new Attempt<>(MlOutcome.OK, value, body.path("model_version").asText(null),
                confidence.isNumber() ? confidence.asDouble() : null, summary, elapsed(started));
        } catch (TimeoutException e) {
            future.cancel(true);
            return fail(capability, breaker, MlOutcome.TIMEOUT, started);
        } catch (ExecutionException e) {
            MlOutcome outcome = e.getCause() instanceof HttpTimeoutException ? MlOutcome.TIMEOUT : MlOutcome.ERROR;
            return fail(capability, breaker, outcome, started);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(capability, breaker, MlOutcome.ERROR, started);
        } catch (Exception e) {
            return fail(capability, breaker, MlOutcome.ERROR, started);
        }
    }

    private <T> Attempt<T> fail(MlCapability capability, CircuitBreaker breaker, MlOutcome outcome, long started) {
        if (breaker.onFailure()) {
            log.warn("ml-service circuit opened for {} after repeated failures (last: {})", capability.id(), outcome);
        } else {
            log.debug("ml-service call for {} failed: {}", capability.id(), outcome);
        }
        return Attempt.failed(outcome, elapsed(started));
    }

    private void record(MlCapability capability, PredictionLogEntry.Mode mode, Attempt<?> attempt, PredictionContext context) {
        PredictionContext ctx = context == null ? PredictionContext.NONE : context;
        try {
            predictionLog.record(PredictionLogEntry.builder()
                .userId(ctx.userId())
                .capability(capability.id())
                .mode(mode)
                .outcome(attempt.outcome())
                .modelVersion(attempt.modelVersion())
                .subjectType(ctx.subjectType())
                .subjectId(ctx.subjectId())
                .confidence(Confidence.of(attempt.confidence()))
                .latencyMs(attempt.latencyMs())
                .outputSummary(attempt.summary())
                .build());
        } catch (RuntimeException e) {
            log.warn("Could not write prediction log for {}: {}", capability.id(), e.getClass().getSimpleName());
        }
    }

    private CircuitBreaker breaker(MlCapability capability) {
        return breakers.computeIfAbsent(capability, c ->
            new CircuitBreaker(properties.failureThreshold(), properties.openDuration(), clock));
    }

    private String baseUrl() {
        String url = properties.url().trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static int elapsed(long startedNanos) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    // Shadow calls are best effort: two threads, a short queue, and overflow is dropped.
    private static ExecutorService boundedShadowExecutor() {
        return new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), r -> {
            Thread t = new Thread(r, "ml-shadow");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.DiscardPolicy());
    }

    @PreDestroy
    void shutdown() {
        if (shadowExecutor instanceof ExecutorService service) service.shutdownNow();
    }

    private record Attempt<T>(
        MlOutcome outcome, T value, String modelVersion, Double confidence,
        Map<String, Object> summary, int latencyMs
    ) {
        static <T> Attempt<T> failed(MlOutcome outcome, int latencyMs) {
            return new Attempt<>(outcome, null, null, null, null, latencyMs);
        }
    }
}
