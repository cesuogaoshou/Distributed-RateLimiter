package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;

import java.util.concurrent.atomic.AtomicLong;

public class FixedWindowRateLimiter implements RateLimiter {

    private volatile RateLimiterConfig config;
    private final AtomicLong counter = new AtomicLong();
    private volatile long windowStartMillis;
    private final AtomicLong allowedRequests = new AtomicLong();
    private final AtomicLong rejectedRequests = new AtomicLong();

    public FixedWindowRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.windowStartMillis = System.currentTimeMillis();
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
        refreshWindowIfNeeded();
        while (true) {
            long current = counter.get();
            if (current + permits > config.capacity()) {
                rejectedRequests.incrementAndGet();
                return false;
            }
            if (counter.compareAndSet(current, current + permits)) {
                allowedRequests.incrementAndGet();
                return true;
            }
        }
    }

    @Override
    public long availablePermits() {
        refreshWindowIfNeeded();
        return Math.max(0, config.capacity() - counter.get());
    }

    @Override
    public RateLimiterStats getStats() {
        return new RateLimiterStats(allowedRequests.get(), rejectedRequests.get(), availablePermits());
    }

    @Override
    public synchronized void updateConfig(RateLimiterConfig config) {
        this.config = config;
        this.counter.set(0);
        this.windowStartMillis = System.currentTimeMillis();
    }

    private void refreshWindowIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - windowStartMillis < config.window().toMillis()) {
            return;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now - windowStartMillis >= config.window().toMillis()) {
                counter.set(0);
                windowStartMillis = now;
            }
        }
    }
}
