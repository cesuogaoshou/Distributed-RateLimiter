package com.example.ratelimiter.core;

import com.example.ratelimiter.algorithm.FixedWindowRateLimiter;
import com.example.ratelimiter.algorithm.LeakyBucketRateLimiter;
import com.example.ratelimiter.algorithm.SlidingWindowRateLimiter;
import com.example.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.spi.RateLimiterAlgorithmLoader;
import com.example.ratelimiter.spi.RateLimiterAlgorithmRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterFactory {

    private final Map<String, RateLimiter> registry = new ConcurrentHashMap<>();
    private final RateLimiterAlgorithmRegistry algorithmRegistry;

    @Autowired
    public RateLimiterFactory() {
        this(new RateLimiterAlgorithmLoader().load());
    }

    public RateLimiterFactory(RateLimiterAlgorithmRegistry algorithmRegistry) {
        this.algorithmRegistry = Objects.requireNonNull(algorithmRegistry, "algorithmRegistry must not be null");
    }

    public RateLimiter getOrCreate(String key, RateLimiterConfig config) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return registry.computeIfAbsent(key, ignored -> create(config));
    }

    private RateLimiter create(RateLimiterConfig config) {
        if (config.customAlgorithm() != null) {
            return algorithmRegistry.find(config.customAlgorithm())
                    .map(algorithm -> algorithm.create(config))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown custom rate limiter algorithm: " + config.customAlgorithm()));
        }
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
