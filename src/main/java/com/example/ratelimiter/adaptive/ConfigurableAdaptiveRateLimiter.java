package com.example.ratelimiter.adaptive;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.core.RateLimiterFactory;
import com.example.ratelimiter.stats.RateLimiterStats;

import java.util.Objects;

public class ConfigurableAdaptiveRateLimiter implements RateLimiter, AdaptiveRateLimiter {

    private final AdaptiveRateLimiterConfig adaptiveConfig;
    private final RateLimiter delegate;
    private volatile double currentQps;

    public ConfigurableAdaptiveRateLimiter(AdaptiveRateLimiterConfig adaptiveConfig, RateLimiter delegate) {
        this.adaptiveConfig = Objects.requireNonNull(adaptiveConfig, "adaptiveConfig must not be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.currentQps = adaptiveConfig.initialQps();
    }

    public static ConfigurableAdaptiveRateLimiter create(AdaptiveRateLimiterConfig adaptiveConfig) {
        RateLimiter delegate = new RateLimiterFactory().getOrCreate(
                "adaptive:" + adaptiveConfig.algorithm().name(),
                adaptiveConfig.toRateLimiterConfig()
        );
        return new ConfigurableAdaptiveRateLimiter(adaptiveConfig, delegate);
    }

    @Override
    public boolean tryAcquire() {
        return delegate.tryAcquire();
    }

    @Override
    public boolean tryAcquire(int permits) {
        return delegate.tryAcquire(permits);
    }

    @Override
    public long availablePermits() {
        return delegate.availablePermits();
    }

    @Override
    public RateLimiterStats getStats() {
        return delegate.getStats();
    }

    @Override
    public void updateConfig(RateLimiterConfig config) {
        delegate.updateConfig(config);
        currentQps = config.ratePerSecond();
    }

    @Override
    public double currentQps() {
        return currentQps;
    }

    @Override
    public double minQps() {
        return adaptiveConfig.minQps();
    }

    @Override
    public double maxQps() {
        return adaptiveConfig.maxQps();
    }

    @Override
    public void updateQps(double qps) {
        double boundedQps = Math.max(minQps(), Math.min(maxQps(), qps));
        delegate.updateConfig(adaptiveConfig.toRateLimiterConfig().toBuilder()
                .ratePerSecond(boundedQps)
                .build());
        currentQps = boundedQps;
    }
}
