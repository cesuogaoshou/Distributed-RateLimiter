package com.example.ratelimiter.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class RateLimiterMetricsBinder implements MeterBinder {

    private final RateLimiterMetricsService service;

    public RateLimiterMetricsBinder(RateLimiterMetricsService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("ratelimiter.limiters", service, current -> current.snapshots().size())
                .description("Number of rate limiters created through the current JVM factory")
                .register(registry);
        Gauge.builder("ratelimiter.requests.allowed", service, current -> sumAllowed(current.snapshots()))
                .description("Total allowed requests across current JVM rate limiters")
                .register(registry);
        Gauge.builder("ratelimiter.requests.rejected", service, current -> sumRejected(current.snapshots()))
                .description("Total rejected requests across current JVM rate limiters")
                .register(registry);
        Gauge.builder("ratelimiter.permits.available", service, current -> sumAvailablePermits(current.snapshots()))
                .description("Total available permits across current JVM rate limiters")
                .register(registry);
    }

    private static long sumAllowed(List<RateLimiterMetricsSnapshot> snapshots) {
        return snapshots.stream()
                .mapToLong(RateLimiterMetricsSnapshot::allowedRequests)
                .sum();
    }

    private static long sumRejected(List<RateLimiterMetricsSnapshot> snapshots) {
        return snapshots.stream()
                .mapToLong(RateLimiterMetricsSnapshot::rejectedRequests)
                .sum();
    }

    private static long sumAvailablePermits(List<RateLimiterMetricsSnapshot> snapshots) {
        return snapshots.stream()
                .mapToLong(RateLimiterMetricsSnapshot::availablePermits)
                .sum();
    }
}
