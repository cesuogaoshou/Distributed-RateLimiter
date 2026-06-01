package com.example.ratelimiter.rule;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitRuleTest {

    @Test
    void convertsRuleToRateLimiterConfig() {
        RateLimitRule rule = new RateLimitRule();
        rule.setAlgorithm(AlgorithmType.FIXED_WINDOW);
        rule.setCapacity(5);
        rule.setRatePerSecond(0.0);
        rule.setWindowMillis(2500);
        rule.setPermits(2);

        RateLimiterConfig config = rule.toConfig();

        assertThat(config.algorithm()).isEqualTo(AlgorithmType.FIXED_WINDOW);
        assertThat(config.capacity()).isEqualTo(5);
        assertThat(config.ratePerSecond()).isEqualTo(0.0);
        assertThat(config.window()).isEqualTo(Duration.ofMillis(2500));
        assertThat(rule.getPermits()).isEqualTo(2);
    }

    @Test
    void defaultsMatchRateLimitAnnotationDefaults() {
        RateLimitRule rule = new RateLimitRule();

        RateLimiterConfig config = rule.toConfig();

        assertThat(config.algorithm()).isEqualTo(AlgorithmType.TOKEN_BUCKET);
        assertThat(config.capacity()).isEqualTo(100);
        assertThat(config.ratePerSecond()).isEqualTo(10.0);
        assertThat(config.window()).isEqualTo(Duration.ofMillis(1000));
        assertThat(rule.getPermits()).isEqualTo(1);
    }
}
