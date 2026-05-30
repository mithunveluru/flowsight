package com.flowsight.security;

import com.flowsight.exception.FlowsightException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token bucket rate limiter, keyed by client identifier + bucket name.
 *
 * <p>Buckets refill at a fixed rate up to a configured capacity. A request consumes
 * one token; when the bucket is empty, the request is rejected with HTTP 429.
 *
 * <p>This implementation is intentionally simple — it works per-instance and is
 * not distributed. Adequate for the current single-instance deployment; a future
 * SaaS rollout would swap in Redis-backed Bucket4j with the same interface.
 */
@Component
@Slf4j
public class RateLimiter {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Tuned per endpoint
    // -------------------------------------------------------------------------

    /** Auth endpoints: 5 requests per minute per IP. Reasonable for honest users, painful for brute-force. */
    public void checkAuthAttempt(String clientId) {
        check("auth:" + clientId, 5, 60_000);
    }

    /** CSV / receipt uploads: 30 per hour per user. */
    public void checkUploadAttempt(String clientId) {
        check("upload:" + clientId, 30, 60 * 60_000);
    }

    // -------------------------------------------------------------------------
    // Core token bucket
    // -------------------------------------------------------------------------

    private void check(String key, int capacity, long refillIntervalMillis) {
        TokenBucket bucket = buckets.computeIfAbsent(key,
            k -> new TokenBucket(capacity, refillIntervalMillis));
        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for key {}", key);
            throw new FlowsightException(
                "Too many requests. Please slow down and try again in a moment.",
                HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    /**
     * Simple token bucket — fills to {@code capacity} over {@code refillIntervalMillis}.
     * Each {@link #tryConsume} call rebuilds elapsed-time-proportionate tokens.
     */
    private static final class TokenBucket {
        private final int  capacity;
        private final long refillIntervalMillis;
        private double tokens;
        private long lastRefillEpochMillis;

        TokenBucket(int capacity, long refillIntervalMillis) {
            this.capacity = capacity;
            this.refillIntervalMillis = refillIntervalMillis;
            this.tokens = capacity;
            this.lastRefillEpochMillis = Instant.now().toEpochMilli();
        }

        synchronized boolean tryConsume() {
            long now = Instant.now().toEpochMilli();
            long elapsed = now - lastRefillEpochMillis;
            if (elapsed > 0) {
                double refill = (elapsed * capacity) / (double) refillIntervalMillis;
                tokens = Math.min(capacity, tokens + refill);
                lastRefillEpochMillis = now;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
