package com.example.ratelimiter.adaptive;

public interface AdaptiveRateLimiter {

    double currentQps();

    double minQps();

    double maxQps();

    void updateQps(double qps);
}
