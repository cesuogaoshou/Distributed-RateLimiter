package com.example.ratelimiter.distributed;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class RedisRateLimiter implements RateLimiter {

    private final String key;
    private final RedisCommandExecutor redis;
    private volatile RateLimiterConfig config;
    private final AtomicLong allowedRequests = new AtomicLong();
    private final AtomicLong rejectedRequests = new AtomicLong();
    private final AtomicLong availablePermits = new AtomicLong();

    public RedisRateLimiter(String key, RateLimiterConfig config, RedisCommandExecutor redis) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.availablePermits.set(config.capacity());
    }

    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    @Override
    public boolean tryAcquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive");
        }
        RateLimiterConfig current = config;
        List<Long> result = redis.evalTokenBucket(
                key,
                current.capacity(),
                current.ratePerSecond(),
                permits,
                System.currentTimeMillis()
        );
        boolean allowed = result.get(0) == 1;
        availablePermits.set(result.get(1));
        if (allowed) {
            allowedRequests.incrementAndGet();
            return true;
        }
        rejectedRequests.incrementAndGet();
        return false;
    }

    @Override
    public long availablePermits() {
        return availablePermits.get();
    }

    @Override
    public RateLimiterStats getStats() {
        return new RateLimiterStats(allowedRequests.get(), rejectedRequests.get(), availablePermits.get());
    }

    @Override
    public void updateConfig(RateLimiterConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.availablePermits.set(config.capacity());
    }
}
