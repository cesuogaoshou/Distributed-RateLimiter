package com.example.ratelimiter.rule;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "ratelimiter")
public class RateLimitProperties {

    private Map<String, RateLimitRule> rules = new HashMap<>();

    public Map<String, RateLimitRule> getRules() {
        return rules;
    }

    public void setRules(Map<String, RateLimitRule> rules) {
        this.rules = rules == null ? new HashMap<>() : new HashMap<>(rules);
    }
}
