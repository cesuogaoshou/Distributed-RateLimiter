package com.example.ratelimiter.metrics;

public record RateLimiterMetricsSnapshot(
        String key,
        long allowedRequests,
        long rejectedRequests,
        long availablePermits
) {
}
