package com.example.ratelimiter.metrics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ratelimit")
public class RateLimiterMetricsController {

    private final RateLimiterMetricsService service;

    public RateLimiterMetricsController(RateLimiterMetricsService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public List<RateLimiterMetricsSnapshot> stats() {
        return service.snapshots();
    }
}
