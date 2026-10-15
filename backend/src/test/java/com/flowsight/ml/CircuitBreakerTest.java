package com.flowsight.ml;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest {

    private final MlClientTest.MutableClock clock = new MlClientTest.MutableClock();
    private final CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(10), clock);

    @Test
    void successResetsTheConsecutiveCount() {
        breaker.onFailure();
        breaker.onFailure();
        breaker.onSuccess();
        breaker.onFailure();
        breaker.onFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.onFailure()).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void halfOpenAdmitsExactlyOneProbe() {
        for (int i = 0; i < 3; i++) breaker.onFailure();
        assertThat(breaker.tryAcquire()).isFalse();

        clock.advance(Duration.ofSeconds(10));

        assertThat(breaker.tryAcquire()).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(breaker.tryAcquire()).isFalse();
    }

    @Test
    void lateFailuresWhileOpenDoNotExtendTheOpenWindow() {
        for (int i = 0; i < 3; i++) breaker.onFailure();
        clock.advance(Duration.ofSeconds(9));
        assertThat(breaker.onFailure()).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(breaker.tryAcquire()).isTrue();
    }
}
