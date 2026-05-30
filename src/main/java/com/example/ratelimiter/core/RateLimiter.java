package com.example.ratelimiter.core;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.stats.RateLimiterStats;

public interface RateLimiter {

    boolean tryAcquire();

    boolean tryAcquire(int permits);

    long availablePermits();

    RateLimiterStats getStats();

    void updateConfig(RateLimiterConfig config);
}
