package com.example.ratelimiter.core;

import com.example.ratelimiter.algorithm.FixedWindowRateLimiter;
import com.example.ratelimiter.algorithm.LeakyBucketRateLimiter;
import com.example.ratelimiter.algorithm.SlidingWindowRateLimiter;
import com.example.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.example.ratelimiter.config.RateLimiterConfig;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterFactory {

    private final Map<String, RateLimiter> registry = new ConcurrentHashMap<>();

    public RateLimiter getOrCreate(String key, RateLimiterConfig config) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return registry.computeIfAbsent(key, ignored -> create(config));
    }

    private RateLimiter create(RateLimiterConfig config) {
        return switch (config.algorithm()) {
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(config);
            case LEAKY_BUCKET -> new LeakyBucketRateLimiter(config);
            case FIXED_WINDOW -> new FixedWindowRateLimiter(config);
            case SLIDING_WINDOW -> new SlidingWindowRateLimiter(config);
            case DISTRIBUTED_TOKEN_BUCKET -> throw new IllegalArgumentException(
                    "distributed token bucket requires RedisRateLimiter with a RedisCommandExecutor"
            );
        };
    }
}
