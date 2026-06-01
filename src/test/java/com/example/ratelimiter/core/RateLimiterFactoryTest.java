package com.example.ratelimiter.core;

import com.example.ratelimiter.algorithm.FixedWindowRateLimiter;
import com.example.ratelimiter.algorithm.LeakyBucketRateLimiter;
import com.example.ratelimiter.algorithm.SlidingWindowRateLimiter;
import com.example.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.spi.RateLimiterAlgorithm;
import com.example.ratelimiter.spi.RateLimiterAlgorithmRegistry;
import com.example.ratelimiter.stats.RateLimiterStats;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterFactoryTest {

    private final RateLimiterFactory factory = new RateLimiterFactory();

    @Test
    void createsTokenBucket() {
        assertThat(factory.getOrCreate("token", config(AlgorithmType.TOKEN_BUCKET)))
                .isInstanceOf(TokenBucketRateLimiter.class);
    }

    @Test
    void createsLeakyBucket() {
        assertThat(factory.getOrCreate("leaky", config(AlgorithmType.LEAKY_BUCKET)))
                .isInstanceOf(LeakyBucketRateLimiter.class);
    }

    @Test
    void createsFixedWindow() {
        assertThat(factory.getOrCreate("fixed", config(AlgorithmType.FIXED_WINDOW)))
                .isInstanceOf(FixedWindowRateLimiter.class);
    }

    @Test
    void createsSlidingWindow() {
        assertThat(factory.getOrCreate("sliding", config(AlgorithmType.SLIDING_WINDOW)))
                .isInstanceOf(SlidingWindowRateLimiter.class);
    }

    @Test
    void reusesLimiterByKey() {
        RateLimiter first = factory.getOrCreate("same", config(AlgorithmType.TOKEN_BUCKET));
        RateLimiter second = factory.getOrCreate("same", config(AlgorithmType.TOKEN_BUCKET));

        assertThat(second).isSameAs(first);
    }

    @Test
    void rejectsDistributedLimiterWithoutRedisExecutor() {
        assertThatThrownBy(() -> factory.getOrCreate("distributed", config(AlgorithmType.DISTRIBUTED_TOKEN_BUCKET)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distributed token bucket requires RedisRateLimiter");
    }

    @Test
    void createsCustomLimiterFromSpiRegistry() {
        NoopRateLimiter limiter = new NoopRateLimiter();
        RateLimiterFactory customFactory = new RateLimiterFactory(new RateLimiterAlgorithmRegistry(List.of(
                new TestAlgorithm("custom", limiter)
        )));

        RateLimiter created = customFactory.getOrCreate("custom", RateLimiterConfig.customAlgorithm("custom").build());

        assertThat(created).isSameAs(limiter);
    }

    @Test
    void rejectsUnknownCustomAlgorithm() {
        RateLimiterFactory customFactory = new RateLimiterFactory(new RateLimiterAlgorithmRegistry(List.of()));

        assertThatThrownBy(() -> customFactory.getOrCreate("missing", RateLimiterConfig.customAlgorithm("missing").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown custom rate limiter algorithm: missing");
    }

    @Test
    void builtInAlgorithmsDoNotRequireSpiRegistry() {
        RateLimiterFactory customFactory = new RateLimiterFactory(new RateLimiterAlgorithmRegistry(List.of()));

        assertThat(customFactory.getOrCreate("token-with-empty-registry", config(AlgorithmType.TOKEN_BUCKET)))
                .isInstanceOf(TokenBucketRateLimiter.class);
    }

    private static RateLimiterConfig config(AlgorithmType algorithm) {
        return RateLimiterConfig.builder(algorithm)
                .capacity(10)
                .ratePerSecond(1.0)
                .window(Duration.ofSeconds(1))
                .build();
    }

    private record TestAlgorithm(String name, RateLimiter limiter) implements RateLimiterAlgorithm {

        @Override
        public RateLimiter create(RateLimiterConfig config) {
            return limiter;
        }
    }

    private static class NoopRateLimiter implements RateLimiter {

        @Override
        public boolean tryAcquire() {
            return true;
        }

        @Override
        public boolean tryAcquire(int permits) {
            return true;
        }

        @Override
        public long availablePermits() {
            return Long.MAX_VALUE;
        }

        @Override
        public RateLimiterStats getStats() {
            return new RateLimiterStats(0, 0, Long.MAX_VALUE);
        }

        @Override
        public void updateConfig(RateLimiterConfig config) {
        }
    }
}
