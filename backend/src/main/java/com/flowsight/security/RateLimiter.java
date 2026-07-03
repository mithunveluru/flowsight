package com.flowsight.security;

import com.flowsight.exception.FlowsightException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// In-memory token-bucket rate limiter, keyed by client + bucket. Per-instance, not distributed.
@Component
@Slf4j
public class RateLimiter {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    // auth: 5/min/IP
    public void checkAuthAttempt(String clientId) {
        check("auth:" + clientId, 5, 60_000);
    }

    // uploads: 30/hour/user
    public void checkUploadAttempt(String clientId) {
        check("upload:" + clientId, 30, 60 * 60_000);
    }

    // reset requests: 5/hour/IP
    public void checkPasswordResetRequest(String clientId) {
        check("pwreset-req:" + clientId, 5, 60 * 60_000);
    }

    // reset confirms: 10/hour/IP, slows token brute force
    public void checkPasswordResetConfirm(String clientId) {
        check("pwreset-confirm:" + clientId, 10, 60 * 60_000);
    }

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

    // token bucket; refills proportional to elapsed time
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
