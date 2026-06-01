package com.example.ratelimiter.spi;

import com.example.ratelimiter.rule.RateLimitRule;

import java.util.Optional;

public class EmptyRuleProvider implements RuleProvider {

    @Override
    public Optional<RateLimitRule> findRule(String key) {
        return Optional.empty();
    }
}
