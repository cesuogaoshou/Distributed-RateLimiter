package com.example.ratelimiter.spi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class RateLimiterAlgorithmRegistry {

    private final Map<String, RateLimiterAlgorithm> algorithms;

    public RateLimiterAlgorithmRegistry(Iterable<RateLimiterAlgorithm> algorithms) {
        Objects.requireNonNull(algorithms, "algorithms must not be null");
        Map<String, RateLimiterAlgorithm> selected = new HashMap<>();
        for (RateLimiterAlgorithm algorithm : algorithms) {
            Objects.requireNonNull(algorithm, "algorithm must not be null");
            String name = normalizeName(algorithm.name());
            selected.merge(name, algorithm, (existing, candidate) ->
                    candidate.priority() > existing.priority() ? candidate : existing);
        }
        this.algorithms = Collections.unmodifiableMap(selected);
    }

    public Optional<RateLimiterAlgorithm> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(algorithms.get(name.trim()));
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("algorithm name must not be blank");
        }
        return name.trim();
    }
}
