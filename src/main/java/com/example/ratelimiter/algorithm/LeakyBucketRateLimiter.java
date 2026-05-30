package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;

public class LeakyBucketRateLimiter implements RateLimiter {

    private RateLimiterConfig config;
    private double waterLevel;
    private long lastDrainNanos;
    private long allowedRequests;
    private long rejectedRequests;

    public LeakyBucketRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.lastDrainNanos = System.nanoTime();
    }

    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    @Override
    public synchronized boolean tryAcquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive");
        }
        drain();
        if (waterLevel + permits <= config.capacity()) {
            waterLevel += permits;
            allowedRequests++;
            return true;
        }
        rejectedRequests++;
        return false;
    }

    @Override
    public synchronized long availablePermits() {
        drain();
        return Math.max(0, config.capacity() - (long) Math.ceil(waterLevel));
    }

    @Override
    public synchronized RateLimiterStats getStats() {
        drain();
        return new RateLimiterStats(allowedRequests, rejectedRequests, availablePermits());
    }

    @Override
    public synchronized void updateConfig(RateLimiterConfig config) {
        this.config = config;
        this.waterLevel = Math.min(waterLevel, config.capacity());
        this.lastDrainNanos = System.nanoTime();
    }

    private void drain() {
        if (config.ratePerSecond() <= 0) {
            return;
        }
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastDrainNanos) / 1_000_000_000.0;
        if (elapsedSeconds <= 0) {
            return;
        }
        waterLevel = Math.max(0, waterLevel - elapsedSeconds * config.ratePerSecond());
        lastDrainNanos = now;
    }
}
