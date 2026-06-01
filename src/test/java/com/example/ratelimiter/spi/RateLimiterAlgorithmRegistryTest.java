package com.example.ratelimiter.spi;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterAlgorithmRegistryTest {

    @Test
    void findsAlgorithmByName() {
        RateLimiterAlgorithm algorithm = new TestAlgorithm("custom", 1);
        RateLimiterAlgorithmRegistry registry = new RateLimiterAlgorithmRegistry(List.of(algorithm));

        assertThat(registry.find("custom")).containsSame(algorithm);
    }

    @Test
    void returnsEmptyForMissingAlgorithm() {
        RateLimiterAlgorithmRegistry registry = new RateLimiterAlgorithmRegistry(List.of());

        assertThat(registry.find("missing")).isEmpty();
    }

    @Test
    void keepsHighestPriorityForDuplicateNames() {
        RateLimiterAlgorithm low = new TestAlgorithm("custom", 1);
        RateLimiterAlgorithm high = new TestAlgorithm("custom", 10);

        RateLimiterAlgorithmRegistry registry = new RateLimiterAlgorithmRegistry(List.of(low, high));

        assertThat(registry.find("custom")).containsSame(high);
    }

    @Test
    void rejectsBlankAlgorithmName() {
        assertThatThrownBy(() -> new RateLimiterAlgorithmRegistry(List.of(new TestAlgorithm(" ", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("algorithm name must not be blank");
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
