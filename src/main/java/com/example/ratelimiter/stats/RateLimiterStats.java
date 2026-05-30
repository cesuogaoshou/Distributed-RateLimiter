package com.example.ratelimiter.stats;

public record RateLimiterStats(
        long allowedRequests,
        long rejectedRequests,
        long availablePermits
) {
}
