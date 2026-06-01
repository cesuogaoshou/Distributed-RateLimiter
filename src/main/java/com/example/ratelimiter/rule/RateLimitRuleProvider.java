package com.example.ratelimiter.rule;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitRuleProvider {

    private final RateLimitProperties properties;

    public RateLimitRuleProvider(RateLimitProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public Optional<RateLimitRule> findRule(String key) {
        return Optional.ofNullable(properties.getRules().get(key));
    }
}
