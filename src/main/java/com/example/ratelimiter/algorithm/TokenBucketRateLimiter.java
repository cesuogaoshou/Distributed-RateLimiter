package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;

public class TokenBucketRateLimiter implements RateLimiter {

    private RateLimiterConfig config;
    private double currentTokens;
    private long lastRefillNanos;
    private long allowedRequests;
    private long rejectedRequests;

    public TokenBucketRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.currentTokens = config.capacity();
        this.lastRefillNanos = System.nanoTime();
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
        refill();
        if (currentTokens >= permits) {
            currentTokens -= permits;
            allowedRequests++;
            return true;
        }
        rejectedRequests++;
        return false;
    }

    @Override
    public synchronized long availablePermits() {
        refill();
        return (long) currentTokens;
    }

    @Override
    public synchronized RateLimiterStats getStats() {
        refill();
        return new RateLimiterStats(allowedRequests, rejectedRequests, (long) currentTokens);
    }

    @Override
    public synchronized void updateConfig(RateLimiterConfig config) {
        this.config = config;
        this.currentTokens = config.capacity();
        this.lastRefillNanos = System.nanoTime();
    }

    private void refill() {
        if (config.ratePerSecond() <= 0) {
            return;
        }
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        if (elapsedSeconds <= 0) {
            return;
        }
        currentTokens = Math.min(config.capacity(), currentTokens + elapsedSeconds * config.ratePerSecond());
        lastRefillNanos = now;
    }
}
