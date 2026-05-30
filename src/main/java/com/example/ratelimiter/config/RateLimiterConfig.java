package com.example.ratelimiter.config;

import com.example.ratelimiter.exception.RateLimiterConfigException;

import java.time.Duration;
import java.util.Objects;

public record RateLimiterConfig(
        AlgorithmType algorithm,
        long capacity,
        double ratePerSecond,
        Duration window
) {

    public RateLimiterConfig {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(window, "window must not be null");
        if (capacity <= 0) {
            throw new RateLimiterConfigException("capacity must be positive");
        }
        if (ratePerSecond < 0) {
            throw new RateLimiterConfigException("ratePerSecond must not be negative");
        }
        if (window.isZero() || window.isNegative()) {
            throw new RateLimiterConfigException("window must be positive");
        }
    }

    public static Builder builder(AlgorithmType algorithm) {
        return new Builder(algorithm);
    }

    public Builder toBuilder() {
        return new Builder(algorithm)
                .capacity(capacity)
                .ratePerSecond(ratePerSecond)
                .window(window);
    }

    public static final class Builder {
        private final AlgorithmType algorithm;
        private long capacity = 100;
        private double ratePerSecond = 10.0;
        private Duration window = Duration.ofSeconds(1);

        private Builder(AlgorithmType algorithm) {
            this.algorithm = algorithm;
        }

        public Builder capacity(long capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder ratePerSecond(double ratePerSecond) {
            this.ratePerSecond = ratePerSecond;
            return this;
        }

        public Builder window(Duration window) {
            this.window = window;
            return this;
        }

        public RateLimiterConfig build() {
            return new RateLimiterConfig(algorithm, capacity, ratePerSecond, window);
        }
    }
}
