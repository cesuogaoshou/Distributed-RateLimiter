package com.example.ratelimiter.adaptive;

import java.util.List;
import java.util.Objects;

public class AdaptiveRateLimiterScheduler {

    private final SystemMetricsProvider metricsProvider;
    private final PIDController pidController;
    private final List<AdaptiveRateLimiter> limiters;

    public AdaptiveRateLimiterScheduler(
            SystemMetricsProvider metricsProvider,
            PIDController pidController,
            List<AdaptiveRateLimiter> limiters) {
        this.metricsProvider = Objects.requireNonNull(metricsProvider, "metricsProvider must not be null");
        this.pidController = Objects.requireNonNull(pidController, "pidController must not be null");
        this.limiters = List.copyOf(Objects.requireNonNull(limiters, "limiters must not be null"));
    }

    public void adjust(double deltaSeconds) {
        SystemMetrics metrics = metricsProvider.collect();
        double adjustment = pidController.calculate(metrics.cpuLoad(), deltaSeconds);
        for (AdaptiveRateLimiter limiter : limiters) {
            double newQps = limiter.currentQps() * (1 + adjustment);
            limiter.updateQps(clamp(newQps, limiter.minQps(), limiter.maxQps()));
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
