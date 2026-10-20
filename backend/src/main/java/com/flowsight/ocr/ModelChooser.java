package com.flowsight.ocr;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Picks the first configured model the provider actually serves. Providers retire model names
 * without notice, so the list of available models is fetched (and cached) instead of trusted, and
 * a model the provider rejects at call time is skipped until the cache next refreshes.
 */
final class ModelChooser {

    static final Duration TTL = Duration.ofHours(1);
    // a failed lookup is retried sooner than a successful one is refreshed
    static final Duration FAILED_LOOKUP_TTL = Duration.ofMinutes(5);

    private final List<String> preferred;
    private final Supplier<Optional<Set<String>>> lookup;
    private final Clock clock;
    private final Map<String, Instant> rejected = new ConcurrentHashMap<>();
    private volatile Set<String> available;
    private volatile Instant expires = Instant.MIN;
    private volatile Instant refreshedAt = Instant.MIN;

    ModelChooser(String configured, Supplier<Optional<Set<String>>> lookup, Clock clock) {
        this.preferred = parse(configured);
        this.lookup = lookup;
        this.clock = clock;
    }

    static List<String> parse(String configured) {
        if (configured == null) return List.of();
        return Arrays.stream(configured.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    List<String> preferred() {
        return preferred;
    }

    /** The model to call, or empty when none of the configured models can be used right now. */
    Optional<String> choose() {
        refreshIfDue();
        Set<String> known = available;
        return preferred.stream()
            .filter(m -> !isRejected(m))
            .filter(m -> known == null || known.contains(m))
            .findFirst();
    }

    /** The provider answered "model not found" (or similar) for this model. */
    void reject(String model) {
        rejected.put(model, clock.instant());
    }

    // a rejection is forgotten once the model list has been fetched again after it
    private boolean isRejected(String model) {
        Instant at = rejected.get(model);
        return at != null && !at.isBefore(refreshedAt);
    }

    private void refreshIfDue() {
        Instant now = clock.instant();
        if (now.isBefore(expires)) return;
        synchronized (this) {
            if (now.isBefore(expires)) return;
            Optional<Set<String>> fetched = lookup.get();
            // an unreachable model list does not disable the feature: fall back to trying the names
            available = fetched.orElse(null);
            refreshedAt = now;
            expires = now.plus(fetched.isPresent() ? TTL : FAILED_LOOKUP_TTL);
        }
    }
}
