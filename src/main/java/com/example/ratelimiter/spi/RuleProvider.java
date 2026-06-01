package com.example.ratelimiter.spi;

import com.example.ratelimiter.rule.RateLimitRule;

import java.util.Optional;

public interface RuleProvider {

    Optional<RateLimitRule> findRule(String key);

    default int priority() {
        return 0;
    }
}
