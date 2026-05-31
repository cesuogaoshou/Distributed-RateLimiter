package com.example.ratelimiter.distributed;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;

import java.util.Objects;

public class DegradingRateLimiter implements RateLimiter {

    private final RateLimiter distributed;
    private final RateLimiter localFallback;
    private final RedisHealthChecker healthChecker;

    public DegradingRateLimiter(RateLimiter distributed, RateLimiter localFallback, RedisHealthChecker healthChecker) {
        this.distributed = Objects.requireNonNull(distributed, "distributed must not be null");
        this.localFallback = Objects.requireNonNull(localFallback, "localFallback must not be null");
        this.healthChecker = Objects.requireNonNull(healthChecker, "healthChecker must not be null");
    }

    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    @Override
    public boolean tryAcquire(int permits) {
        if (!healthChecker.isHealthy()) {
            return localFallback.tryAcquire(permits);
        }
        try {
            return distributed.tryAcquire(permits);
        } catch (RedisCommandException ex) {
            healthChecker.markUnhealthy();
            return localFallback.tryAcquire(permits);
        }
    }

    @Override
    public long availablePermits() {
        if (!healthChecker.isHealthy()) {
            return localFallback.availablePermits();
        }
        return distributed.availablePermits();
    }

    @Override
    public RateLimiterStats getStats() {
        if (!healthChecker.isHealthy()) {
            return localFallback.getStats();
        }
        return distributed.getStats();
    }

    @Override
    public void updateConfig(RateLimiterConfig config) {
        distributed.updateConfig(config);
        localFallback.updateConfig(config);
    }
}
