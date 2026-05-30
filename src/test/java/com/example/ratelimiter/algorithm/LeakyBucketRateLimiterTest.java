package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.testsupport.ConcurrentTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LeakyBucketRateLimiterTest {

    @Test
    void acceptsUntilBucketIsFull() {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(config(3, 0.0));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void drainsOverTime() throws InterruptedException {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(config(1, 20.0));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        Thread.sleep(80);
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void supportsBulkPermits() {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(config(5, 0.0));

        assertThat(limiter.tryAcquire(4)).isTrue();
        assertThat(limiter.tryAcquire(2)).isFalse();
    }

    @Test
    void doesNotOverfillUnderConcurrency() throws Exception {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(config(4, 0.0));

        long allowed = ConcurrentTestSupport.runConcurrently(20, limiter::tryAcquire)
                .stream()
                .filter(Boolean::booleanValue)
                .count();

        assertThat(allowed).isEqualTo(4);
    }

    @Test
    void updatesStats() {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(config(1, 0.0));

        limiter.tryAcquire();
        limiter.tryAcquire();

        assertThat(limiter.getStats().allowedRequests()).isEqualTo(1);
        assertThat(limiter.getStats().rejectedRequests()).isEqualTo(1);
    }

    private static RateLimiterConfig config(long capacity, double drainRate) {
        return RateLimiterConfig.builder(AlgorithmType.LEAKY_BUCKET)
                .capacity(capacity)
                .ratePerSecond(drainRate)
                .window(Duration.ofSeconds(1))
                .build();
    }
}
