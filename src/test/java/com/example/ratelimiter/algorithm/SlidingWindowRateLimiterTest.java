package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.testsupport.ConcurrentTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowRateLimiterTest {

    @Test
    void rejectsWhenEventsInsideWindowReachCapacity() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(config(2, Duration.ofMillis(200)));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void evictsExpiredEvents() throws InterruptedException {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(config(1, Duration.ofMillis(50)));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        Thread.sleep(80);
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void supportsBulkPermits() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(config(4, Duration.ofSeconds(1)));

        assertThat(limiter.tryAcquire(3)).isTrue();
        assertThat(limiter.tryAcquire(2)).isFalse();
    }

    @Test
    void doesNotOverAllowUnderConcurrency() throws Exception {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(config(3, Duration.ofSeconds(1)));

        long allowed = ConcurrentTestSupport.runConcurrently(20, limiter::tryAcquire)
                .stream()
                .filter(Boolean::booleanValue)
                .count();

        assertThat(allowed).isEqualTo(3);
    }

    private static RateLimiterConfig config(long capacity, Duration window) {
        return RateLimiterConfig.builder(AlgorithmType.SLIDING_WINDOW)
                .capacity(capacity)
                .ratePerSecond(0.0)
                .window(window)
                .build();
    }
}
