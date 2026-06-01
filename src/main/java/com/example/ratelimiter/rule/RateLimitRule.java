package com.example.ratelimiter.rule;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;

import java.time.Duration;

public class RateLimitRule {

    private AlgorithmType algorithm = AlgorithmType.TOKEN_BUCKET;
    private long capacity = 100;
    private double ratePerSecond = 10.0;
    private long windowMillis = 1000;
    private int permits = 1;

    public RateLimiterConfig toConfig() {
        return RateLimiterConfig.builder(algorithm)
                .capacity(capacity)
                .ratePerSecond(ratePerSecond)
                .window(Duration.ofMillis(windowMillis))
                .build();
    }

    public AlgorithmType getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(AlgorithmType algorithm) {
        this.algorithm = algorithm;
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public double getRatePerSecond() {
        return ratePerSecond;
    }

    public void setRatePerSecond(double ratePerSecond) {
        this.ratePerSecond = ratePerSecond;
    }

    public long getWindowMillis() {
        return windowMillis;
    }

    public void setWindowMillis(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public int getPermits() {
        return permits;
    }

    public void setPermits(int permits) {
        this.permits = permits;
    }
}
