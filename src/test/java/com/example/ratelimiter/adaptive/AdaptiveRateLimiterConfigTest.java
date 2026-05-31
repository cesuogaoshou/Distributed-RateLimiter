package com.example.ratelimiter.adaptive;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.exception.RateLimiterConfigException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptiveRateLimiterConfigTest {

    @Test
    void createsBaseRateLimiterConfigFromAdaptiveSettings() {
        AdaptiveRateLimiterConfig adaptiveConfig = new AdaptiveRateLimiterConfig(
                AlgorithmType.TOKEN_BUCKET,
                100,
                20.0,
                10.0,
                80.0,
                Duration.ofSeconds(1));

        RateLimiterConfig config = adaptiveConfig.toRateLimiterConfig();

        assertThat(config.algorithm()).isEqualTo(AlgorithmType.TOKEN_BUCKET);
        assertThat(config.capacity()).isEqualTo(100);
        assertThat(config.ratePerSecond()).isEqualTo(20.0);
        assertThat(config.window()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void rejectsInitialQpsBelowMinimum() {
        assertThatThrownBy(() -> new AdaptiveRateLimiterConfig(
                AlgorithmType.TOKEN_BUCKET,
                100,
                5.0,
                10.0,
                80.0,
                Duration.ofSeconds(1)))
                .isInstanceOf(RateLimiterConfigException.class)
                .hasMessageContaining("initialQps must be between minQps and maxQps");
    }

    @Test
    void rejectsInvalidQpsBounds() {
        assertThatThrownBy(() -> new AdaptiveRateLimiterConfig(
                AlgorithmType.TOKEN_BUCKET,
                100,
                20.0,
                80.0,
                10.0,
                Duration.ofSeconds(1)))
                .isInstanceOf(RateLimiterConfigException.class)
                .hasMessageContaining("minQps must be positive and not greater than maxQps");
    }
}
