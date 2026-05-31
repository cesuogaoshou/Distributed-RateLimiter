package com.example.ratelimiter.distributed;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisRateLimiterTest {

    @Test
    void allowsUpToDistributedCapacity() {
        RedisRateLimiter limiter = new RedisRateLimiter("api:create-order", config(2, 0.0), new FakeRedisCommandExecutor());

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        assertThat(limiter.availablePermits()).isZero();
    }

    @Test
    void supportsBulkPermits() {
        RedisRateLimiter limiter = new RedisRateLimiter("api:bulk", config(5, 0.0), new FakeRedisCommandExecutor());

        assertThat(limiter.tryAcquire(3)).isTrue();
        assertThat(limiter.availablePermits()).isEqualTo(2);
        assertThat(limiter.tryAcquire(3)).isFalse();
    }

    @Test
    void updatesStats() {
        RedisRateLimiter limiter = new RedisRateLimiter("api:stats", config(1, 0.0), new FakeRedisCommandExecutor());

        limiter.tryAcquire();
        limiter.tryAcquire();

        assertThat(limiter.getStats().allowedRequests()).isEqualTo(1);
        assertThat(limiter.getStats().rejectedRequests()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidPermits() {
        RedisRateLimiter limiter = new RedisRateLimiter("api:invalid", config(1, 0.0), new FakeRedisCommandExecutor());

        assertThatThrownBy(() -> limiter.tryAcquire(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permits must be positive");
    }

    @Test
    void propagatesRedisCommandFailure() {
        FakeRedisCommandExecutor executor = new FakeRedisCommandExecutor();
        executor.setFailCommands(true);
        RedisRateLimiter limiter = new RedisRateLimiter("api:fail", config(1, 0.0), executor);

        assertThatThrownBy(limiter::tryAcquire)
                .isInstanceOf(RedisCommandException.class)
                .hasMessageContaining("simulated redis failure");
    }

    private static RateLimiterConfig config(long capacity, double rate) {
        return RateLimiterConfig.builder(AlgorithmType.DISTRIBUTED_TOKEN_BUCKET)
                .capacity(capacity)
                .ratePerSecond(rate)
                .window(Duration.ofSeconds(1))
                .build();
    }
}
