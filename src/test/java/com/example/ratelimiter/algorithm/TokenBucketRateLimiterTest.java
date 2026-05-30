package com.example.ratelimiter.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.exception.RateLimiterConfigException;
import com.example.ratelimiter.testsupport.ConcurrentTestSupport;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    @Test
    void createsValidTokenBucketConfig() {
        RateLimiterConfig config = RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(10)
                .ratePerSecond(5.0)
                .window(Duration.ofSeconds(1))
                .build();

        assertThat(config.algorithm()).isEqualTo(AlgorithmType.TOKEN_BUCKET);
        assertThat(config.capacity()).isEqualTo(10);
        assertThat(config.ratePerSecond()).isEqualTo(5.0);
        assertThat(config.window()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void rejectsInvalidCapacity() {
        assertThatThrownBy(() -> RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(0)
                .build())
                .isInstanceOf(RateLimiterConfigException.class)
                .hasMessageContaining("capacity must be positive");
    }

    @Test
    void allowsBurstUpToCapacity() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(3)
                .ratePerSecond(1.0)
                .window(Duration.ofSeconds(1))
                .build());

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        assertThat(limiter.availablePermits()).isZero();
    }

    @Test
    void supportsBulkPermits() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(5)
                .ratePerSecond(1.0)
                .window(Duration.ofSeconds(1))
                .build());

        assertThat(limiter.tryAcquire(3)).isTrue();
        assertThat(limiter.availablePermits()).isEqualTo(2);
        assertThat(limiter.tryAcquire(3)).isFalse();
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(2)
                .ratePerSecond(20.0)
                .window(Duration.ofSeconds(1))
                .build());

        assertThat(limiter.tryAcquire(2)).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        Thread.sleep(80);

        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void updatesStats() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(1)
                .ratePerSecond(0.0)
                .window(Duration.ofSeconds(1))
                .build());

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        assertThat(limiter.getStats().allowedRequests()).isEqualTo(1);
        assertThat(limiter.getStats().rejectedRequests()).isEqualTo(1);
    }

    @Test
    void doesNotOverIssueUnderConcurrency() throws Exception {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(5)
                .ratePerSecond(0.0)
                .window(Duration.ofSeconds(1))
                .build());

        long allowed = ConcurrentTestSupport.runConcurrently(20, limiter::tryAcquire)
                .stream()
                .filter(Boolean::booleanValue)
                .count();

        assertThat(allowed).isEqualTo(5);
        assertThat(limiter.availablePermits()).isZero();
    }

    @Test
    void updateConfigChangesCapacity() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(1)
                .ratePerSecond(0.0)
                .window(Duration.ofSeconds(1))
                .build());

        assertThat(limiter.tryAcquire()).isTrue();
        limiter.updateConfig(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(3)
                .ratePerSecond(0.0)
                .window(Duration.ofSeconds(1))
                .build());

        assertThat(limiter.availablePermits()).isEqualTo(3);
    }
}
