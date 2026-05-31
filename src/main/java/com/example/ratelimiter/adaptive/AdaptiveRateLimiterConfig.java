package com.example.ratelimiter.adaptive;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.exception.RateLimiterConfigException;

import java.time.Duration;
import java.util.Objects;

public record AdaptiveRateLimiterConfig(
        AlgorithmType algorithm,
        long capacity,
        double initialQps,
        double minQps,
        double maxQps,
        Duration window
) {

    public AdaptiveRateLimiterConfig {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(window, "window must not be null");
        if (minQps <= 0 || !Double.isFinite(minQps) || !Double.isFinite(maxQps) || minQps > maxQps) {
            throw new RateLimiterConfigException("minQps must be positive and not greater than maxQps");
        }
        if (!Double.isFinite(initialQps) || initialQps < minQps || initialQps > maxQps) {
            throw new RateLimiterConfigException("initialQps must be between minQps and maxQps");
        }
    }

    public RateLimiterConfig toRateLimiterConfig() {
        return RateLimiterConfig.builder(algorithm)
                .capacity(capacity)
                .ratePerSecond(initialQps)
                .window(window)
                .build();
    }
}
