package com.flowsight.ml;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Opens after N consecutive failures; after the open duration a single probe call is let
 * through (half-open). The probe's success closes the circuit, its failure reopens it.
 * Every acquired call must report exactly one onSuccess or onFailure.
 */
final class CircuitBreaker {

    enum State { CLOSED, OPEN, HALF_OPEN }

    private final int      failureThreshold;
    private final Duration openDuration;
    private final Clock    clock;

    private State   state = State.CLOSED;
    private int     consecutiveFailures;
    private Instant openedAt;

    CircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration     = openDuration;
        this.clock            = clock;
    }

    synchronized boolean tryAcquire() {
        return switch (state) {
            case CLOSED -> true;
            case HALF_OPEN -> false;
            case OPEN -> {
                if (clock.instant().isBefore(openedAt.plus(openDuration))) yield false;
                state = State.HALF_OPEN;
                yield true;
            }
        };
    }

    synchronized void onSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
    }

    // Returns true when this failure opened the circuit.
    synchronized boolean onFailure() {
        if (state == State.OPEN) return false;
        if (state == State.HALF_OPEN || ++consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = clock.instant();
            consecutiveFailures = 0;
            return true;
        }
        return false;
    }

    synchronized State state() {
        return state;
    }
}
