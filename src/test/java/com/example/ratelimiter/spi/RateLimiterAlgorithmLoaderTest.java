package com.example.ratelimiter.spi;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterAlgorithmLoaderTest {

    @Test
    void returnsEmptyRegistryWhenNoProvidersExist() {
        RateLimiterAlgorithmRegistry registry = new RateLimiterAlgorithmLoader(List.of()).load();

        assertThat(registry.find("missing")).isEmpty();
    }

    @Test
    void loadsProvidersIntoRegistry() {
        RateLimiterAlgorithm algorithm = new TestAlgorithm("custom", 1);

        RateLimiterAlgorithmRegistry registry = new RateLimiterAlgorithmLoader(List.of(algorithm)).load();

        assertThat(registry.find("custom")).containsSame(algorithm);
    }

    private record TestAlgorithm(String name, int priority) implements RateLimiterAlgorithm {

        @Override
        public RateLimiter create(RateLimiterConfig config) {
            return new NoopRateLimiter();
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
