package com.example.ratelimiter.spi;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;

public interface RateLimiterAlgorithm {

    String name();

    RateLimiter create(RateLimiterConfig config);

    default int priority() {
        return 0;
    }
}
