package com.example.ratelimiter.distributed;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class RedisHealthChecker {

    private final RedisCommandExecutor redis;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    public RedisHealthChecker(RedisCommandExecutor redis) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
    }

    public boolean refresh() {
        boolean current = redis.ping();
        healthy.set(current);
        return current;
    }

    public boolean isHealthy() {
        return healthy.get();
    }

    public void markUnhealthy() {
        healthy.set(false);
    }
}
