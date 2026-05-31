package com.example.ratelimiter.distributed;

import com.example.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DegradingRateLimiterTest {

    @Test
    void usesRedisWhenHealthy() {
        FakeRedisCommandExecutor executor = new FakeRedisCommandExecutor();
        RedisHealthChecker healthChecker = new RedisHealthChecker(executor);
        DegradingRateLimiter limiter = limiter(executor, healthChecker, 1, 10);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        assertThat(limiter.getStats().allowedRequests()).isEqualTo(1);
        assertThat(limiter.getStats().rejectedRequests()).isEqualTo(1);
    }

    @Test
    void fallsBackToLocalWhenRedisUnhealthy() {
        FakeRedisCommandExecutor executor = new FakeRedisCommandExecutor();
        RedisHealthChecker healthChecker = new RedisHealthChecker(executor);
        healthChecker.markUnhealthy();
        DegradingRateLimiter limiter = limiter(executor, healthChecker, 1, 2);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void marksRedisUnhealthyWhenDistributedAcquireThrows() {
        FakeRedisCommandExecutor executor = new FakeRedisCommandExecutor();
        executor.setFailCommands(true);
        RedisHealthChecker healthChecker = new RedisHealthChecker(executor);
        DegradingRateLimiter limiter = limiter(executor, healthChecker, 1, 2);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(healthChecker.isHealthy()).isFalse();
    }

    @Test
    void refreshCanRestoreRedisHealth() {
        FakeRedisCommandExecutor executor = new FakeRedisCommandExecutor();
        RedisHealthChecker healthChecker = new RedisHealthChecker(executor);
        healthChecker.markUnhealthy();

        assertThat(healthChecker.refresh()).isTrue();
        assertThat(healthChecker.isHealthy()).isTrue();
    }

    private static DegradingRateLimiter limiter(
            FakeRedisCommandExecutor executor,
            RedisHealthChecker healthChecker,
            long distributedCapacity,
            long localCapacity) {
        RedisRateLimiter distributed = new RedisRateLimiter(
                "api:degrade",
                config(AlgorithmType.DISTRIBUTED_TOKEN_BUCKET, distributedCapacity),
                executor
        );
        TokenBucketRateLimiter local = new TokenBucketRateLimiter(config(AlgorithmType.TOKEN_BUCKET, localCapacity));
        return new DegradingRateLimiter(distributed, local, healthChecker);
    }

    private static RateLimiterConfig config(AlgorithmType algorithm, long capacity) {
        return RateLimiterConfig.builder(algorithm)
                .capacity(capacity)
                .ratePerSecond(0.0)
                .window(Duration.ofSeconds(1))
                .build();
    }
}
