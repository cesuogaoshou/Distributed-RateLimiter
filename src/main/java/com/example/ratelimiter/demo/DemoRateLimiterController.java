package com.example.ratelimiter.demo;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.core.RateLimiterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/demo")
public class DemoRateLimiterController {

    private static final String ORDER_KEY = "demo:orders";

    private final RateLimiterFactory rateLimiterFactory;

    public DemoRateLimiterController(RateLimiterFactory rateLimiterFactory) {
        this.rateLimiterFactory = rateLimiterFactory;
    }

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder() {
        RateLimiter limiter = rateLimiterFactory.getOrCreate(ORDER_KEY, RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(5)
                .ratePerSecond(1.0)
                .window(Duration.ofSeconds(1))
                .build());
        if (!limiter.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "key", ORDER_KEY,
                            "status", "rate_limited"
                    ));
        }
        return ResponseEntity.ok(Map.of(
                "key", ORDER_KEY,
                "status", "created"
        ));
    }
}
