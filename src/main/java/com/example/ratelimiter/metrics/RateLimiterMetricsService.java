package com.example.ratelimiter.metrics;

import com.example.ratelimiter.core.RateLimiterFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class RateLimiterMetricsService {

    private final RateLimiterFactory factory;

    public RateLimiterMetricsService(RateLimiterFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
    }

    public List<RateLimiterMetricsSnapshot> snapshots() {
        return factory.snapshotStats().values().stream()
                .sorted(Comparator.comparing(RateLimiterMetricsSnapshot::key))
                .toList();
    }
}
