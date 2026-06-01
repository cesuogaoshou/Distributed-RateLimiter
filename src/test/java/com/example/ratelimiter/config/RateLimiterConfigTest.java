package com.example.ratelimiter.config;

import com.example.ratelimiter.exception.RateLimiterConfigException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterConfigTest {

    @Test
    void buildsBuiltinAlgorithmConfig() {
        RateLimiterConfig config = RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(20)
                .ratePerSecond(2.0)
                .window(Duration.ofSeconds(2))
                .build();

        assertThat(config.algorithm()).isEqualTo(AlgorithmType.TOKEN_BUCKET);
        assertThat(config.customAlgorithm()).isNull();
        assertThat(config.capacity()).isEqualTo(20);
        assertThat(config.ratePerSecond()).isEqualTo(2.0);
        assertThat(config.window()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void buildsCustomAlgorithmConfig() {
        RateLimiterConfig config = RateLimiterConfig.customAlgorithm("custom-bucket")
                .capacity(10)
                .ratePerSecond(1.0)
                .window(Duration.ofSeconds(1))
                .build();

        assertThat(config.algorithm()).isNull();
        assertThat(config.customAlgorithm()).isEqualTo("custom-bucket");
    }

    @Test
    void trimsCustomAlgorithmName() {
        RateLimiterConfig config = RateLimiterConfig.customAlgorithm("  custom-bucket  ").build();

        assertThat(config.customAlgorithm()).isEqualTo("custom-bucket");
    }

    @Test
    void rejectsBlankCustomAlgorithmName() {
        assertThatThrownBy(() -> RateLimiterConfig.customAlgorithm(" ").build())
                .isInstanceOf(RateLimiterConfigException.class)
                .hasMessageContaining("customAlgorithm must not be blank");
    }

    @Test
    void rejectsNullBuiltinAlgorithm() {
        assertThatThrownBy(() -> RateLimiterConfig.builder(null).build())
                .isInstanceOf(RateLimiterConfigException.class)
                .hasMessageContaining("algorithm must not be null");
    }

    @Test
    void toBuilderPreservesCustomAlgorithmPath() {
        RateLimiterConfig updated = RateLimiterConfig.customAlgorithm("custom-bucket")
                .capacity(10)
                .build()
                .toBuilder()
                .capacity(20)
                .build();

        assertThat(updated.algorithm()).isNull();
        assertThat(updated.customAlgorithm()).isEqualTo("custom-bucket");
        assertThat(updated.capacity()).isEqualTo(20);
    }
}
