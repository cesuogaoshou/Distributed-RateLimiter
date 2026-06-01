package com.example.ratelimiter.config;

import com.example.ratelimiter.exception.RateLimiterConfigException;

import java.time.Duration;
import java.util.Objects;

public record RateLimiterConfig(
        AlgorithmType algorithm,
        String customAlgorithm,
        long capacity,
        double ratePerSecond,
        Duration window
) {

    public RateLimiterConfig {
        Objects.requireNonNull(window, "window must not be null");
        if (algorithm == null) {
            if (customAlgorithm == null || customAlgorithm.isBlank()) {
                throw new RateLimiterConfigException("customAlgorithm must not be blank");
            }
            customAlgorithm = customAlgorithm.trim();
        } else if (customAlgorithm != null) {
            throw new RateLimiterConfigException("customAlgorithm must be null for built-in algorithms");
        }
        if (capacity <= 0) {
            throw new RateLimiterConfigException("capacity must be positive");
        }
        if (!Double.isFinite(ratePerSecond) || ratePerSecond < 0) {
            throw new RateLimiterConfigException("ratePerSecond must be finite and not negative");
        }
        if (window.isZero() || window.isNegative()) {
            throw new RateLimiterConfigException("window must be positive");
        }
    }

    public static Builder builder(AlgorithmType algorithm) {
        if (algorithm == null) {
            throw new RateLimiterConfigException("algorithm must not be null");
        }
        return new Builder(algorithm, null);
    }

    public static Builder customAlgorithm(String customAlgorithm) {
        return new Builder(null, customAlgorithm);
    }

    public Builder toBuilder() {
        return new Builder(algorithm, customAlgorithm)
                .capacity(capacity)
                .ratePerSecond(ratePerSecond)
                .window(window);
    }

    public static final class Builder {
        private final AlgorithmType algorithm;
        private final String customAlgorithm;
        private long capacity = 100;
        private double ratePerSecond = 10.0;
        private Duration window = Duration.ofSeconds(1);

        private Builder(AlgorithmType algorithm, String customAlgorithm) {
            this.algorithm = algorithm;
            this.customAlgorithm = customAlgorithm;
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
            return new RateLimiterConfig(algorithm, customAlgorithm, capacity, ratePerSecond, window);
        }
    }
}
