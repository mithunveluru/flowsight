package com.flowsight.ml.forecast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Global error quantiles per (series kind, complete months of history, steps ahead), calibrated
 * offline by ml-service/app/forecaster/calibrate.py on seeded synthetic histories. The same file
 * ships as ml-service/app/forecaster/artifact/priors.json.
 */
public final class ForecastPriors {

    public record Prior(double q025, double q10, double q90, double q975, double mad) {}

    private static final String RESOURCE = "/com/flowsight/ml/forecast/priors.json";
    private static final int MAX_STEP = 4;

    private final String version;
    private final Map<String, Prior> priors;

    ForecastPriors(String version, Map<String, Prior> priors) {
        this.version = version;
        this.priors = priors;
    }

    public static ForecastPriors bundled() {
        try (InputStream in = ForecastPriors.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("missing " + RESOURCE);
            return parse(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ForecastPriors parse(JsonNode root) {
        Map<String, Prior> out = new HashMap<>();
        root.path("q").fields().forEachRemaining(e -> {
            JsonNode q = e.getValue();
            out.put(e.getKey(), new Prior(q.path("0.025").asDouble(), q.path("0.1").asDouble(),
                q.path("0.9").asDouble(), q.path("0.975").asDouble(),
                root.path("mad").path(e.getKey()).asDouble()));
        });
        return new ForecastPriors(root.path("version").asText("unknown"), Map.copyOf(out));
    }

    public String version() {
        return version;
    }

    public Prior prior(SeriesKind kind, int months, int step) {
        Prior p = priors.get(key(kind, months, Math.min(step, MAX_STEP)));
        if (p == null) throw new IllegalStateException("no forecast prior for " + key(kind, months, step));
        return p;
    }

    static String key(SeriesKind kind, int months, int step) {
        return kind.id() + "|" + bucket(months) + "|" + step;
    }

    static String bucket(int months) {
        if (months <= 3) return Integer.toString(months);
        if (months <= 6) return "4-6";
        if (months <= 12) return "7-12";
        return "13+";
    }
}
