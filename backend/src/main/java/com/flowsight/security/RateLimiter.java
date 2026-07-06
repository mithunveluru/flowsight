package com.flowsight.security;

import com.flowsight.exception.FlowsightException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

// Fixed-window rate limiter backed by Postgres, keyed by client + bucket.
// State is shared across app instances and survives restarts. The increment is
// a single atomic upsert; REQUIRES_NEW keeps the count from rolling back with
// a caller's failed transaction (a failed register attempt must still count).
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimiter {

    private static final String UPSERT = """
        INSERT INTO rate_limit_windows (bucket_key, window_start, count)
        VALUES (?, ?, 1)
        ON CONFLICT (bucket_key, window_start)
        DO UPDATE SET count = rate_limit_windows.count + 1
        RETURNING count
        """;

    private final JdbcTemplate jdbcTemplate;

    // auth (login/register/refresh/logout): 5/min/IP
    public void checkAuthAttempt(String clientId) {
        check("auth:" + clientId, 5, Duration.ofMinutes(1));
    }

    // uploads: 30/hour/user
    public void checkUploadAttempt(String clientId) {
        check("upload:" + clientId, 30, Duration.ofHours(1));
    }

    // reset requests: 5/hour/IP
    public void checkPasswordResetRequest(String clientId) {
        check("pwreset-req:" + clientId, 5, Duration.ofHours(1));
    }

    // reset confirms: 10/hour/IP, slows token brute force
    public void checkPasswordResetConfirm(String clientId) {
        check("pwreset-confirm:" + clientId, 10, Duration.ofHours(1));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void check(String key, int capacity, Duration window) {
        long windowMillis = window.toMillis();
        long start = (Instant.now().toEpochMilli() / windowMillis) * windowMillis;
        Integer count;
        try {
            count = jdbcTemplate.queryForObject(
                UPSERT, Integer.class, truncateKey(key), Timestamp.from(Instant.ofEpochMilli(start)));
        } catch (Exception e) {
            // fail open on storage trouble: an outage must not lock everyone out
            log.error("Rate limiter storage failure for key {}: {}", key, e.getMessage());
            return;
        }
        if (count != null && count > capacity) {
            log.warn("Rate limit exceeded for key {}", key);
            throw new FlowsightException(
                "Too many requests. Please slow down and try again in a moment.",
                HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    // Purge windows older than a day (longest window is 1 hour).
    @Scheduled(fixedDelayString = "PT1H")
    public void purgeExpired() {
        int deleted = jdbcTemplate.update(
            "DELETE FROM rate_limit_windows WHERE window_start < ?",
            Timestamp.from(Instant.now().minus(Duration.ofDays(1))));
        if (deleted > 0) log.debug("Purged {} stale rate-limit windows", deleted);
    }

    // bucket_key column is VARCHAR(160); keys are "prefix:ip-or-uuid" and normally
    // short, but never let a hostile oversized header break the insert
    private static String truncateKey(String key) {
        return key.length() <= 160 ? key : key.substring(0, 160);
    }
}
