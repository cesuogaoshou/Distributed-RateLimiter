package com.example.ratelimiter.rule;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitRuleProviderTest {

    @Test
    void findsConfiguredRuleByKey() {
        RateLimitRule rule = new RateLimitRule();
        rule.setCapacity(1);
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRules(Map.of("order:create", rule));
        RateLimitRuleProvider provider = new RateLimitRuleProvider(properties);

        assertThat(provider.findRule("order:create")).containsSame(rule);
    }

    @Test
    void returnsEmptyForMissingKey() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRules(Map.of("order:create", new RateLimitRule()));
        RateLimitRuleProvider provider = new RateLimitRuleProvider(properties);

        assertThat(provider.findRule("order:cancel")).isEmpty();
    }

    @Test
    void handlesNullRulesAsEmpty() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRules(null);
        RateLimitRuleProvider provider = new RateLimitRuleProvider(properties);

        assertThat(provider.findRule("anything")).isEmpty();
    }
}
