package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;

import java.util.ArrayDeque;
import java.util.Deque;

public class SlidingWindowRateLimiter implements RateLimiter {

    private RateLimiterConfig config;
    private final Deque<Long> timestamps = new ArrayDeque<>();
    private long allowedRequests;
    private long rejectedRequests;

    public SlidingWindowRateLimiter(RateLimiterConfig config) {
        this.config = config;
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
        long now = System.currentTimeMillis();
        evictExpired(now);
        if (timestamps.size() + permits > config.capacity()) {
            rejectedRequests++;
            return false;
        }
        for (int i = 0; i < permits; i++) {
            timestamps.addLast(now);
        }
        allowedRequests++;
        return true;
    }

    @Override
    public synchronized long availablePermits() {
        evictExpired(System.currentTimeMillis());
        return Math.max(0, config.capacity() - timestamps.size());
    }

    @Override
    public synchronized RateLimiterStats getStats() {
        return new RateLimiterStats(allowedRequests, rejectedRequests, availablePermits());
    }

    @Override
    public synchronized void updateConfig(RateLimiterConfig config) {
        this.config = config;
        timestamps.clear();
    }

    private void evictExpired(long now) {
        long windowMillis = config.window().toMillis();
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMillis) {
            timestamps.removeFirst();
        }
    }
}
