package com.example.ratelimiter.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.exception.RateLimiterConfigException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    @Test
    void createsValidTokenBucketConfig() {
        RateLimiterConfig config = RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(10)
                .ratePerSecond(5.0)
                .window(Duration.ofSeconds(1))
                .build();

        assertThat(config.algorithm()).isEqualTo(AlgorithmType.TOKEN_BUCKET);
        assertThat(config.capacity()).isEqualTo(10);
        assertThat(config.ratePerSecond()).isEqualTo(5.0);
        assertThat(config.window()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void rejectsInvalidCapacity() {
        assertThatThrownBy(() -> RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(0)
                .build())
                .isInstanceOf(RateLimiterConfigException.class)
                .hasMessageContaining("capacity must be positive");
    }
}
