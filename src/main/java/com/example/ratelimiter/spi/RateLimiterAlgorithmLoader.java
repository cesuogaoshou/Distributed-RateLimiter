package com.example.ratelimiter.spi;

import java.util.Objects;
import java.util.ServiceLoader;

public class RateLimiterAlgorithmLoader {

    private final Iterable<RateLimiterAlgorithm> algorithms;

    public RateLimiterAlgorithmLoader() {
        this(ServiceLoader.load(RateLimiterAlgorithm.class));
    }

    public RateLimiterAlgorithmLoader(Iterable<RateLimiterAlgorithm> algorithms) {
        this.algorithms = Objects.requireNonNull(algorithms, "algorithms must not be null");
    }

    public RateLimiterAlgorithmRegistry load() {
        return new RateLimiterAlgorithmRegistry(algorithms);
    }
}
