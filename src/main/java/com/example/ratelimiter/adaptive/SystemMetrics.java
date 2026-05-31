package com.example.ratelimiter.adaptive;

public record SystemMetrics(
        double cpuLoad,
        long heapUsedBytes,
        long heapMaxBytes,
        long currentQps
) {
}
