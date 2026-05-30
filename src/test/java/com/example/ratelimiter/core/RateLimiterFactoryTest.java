package com.example.ratelimiter.core;

import com.example.ratelimiter.algorithm.FixedWindowRateLimiter;
import com.example.ratelimiter.algorithm.LeakyBucketRateLimiter;
import com.example.ratelimiter.algorithm.SlidingWindowRateLimiter;
import com.example.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterFactoryTest {

    private final RateLimiterFactory factory = new RateLimiterFactory();

    @Test
    void createsTokenBucket() {
        assertThat(factory.getOrCreate("token", config(AlgorithmType.TOKEN_BUCKET)))
                .isInstanceOf(TokenBucketRateLimiter.class);
    }

    @Test
    void createsLeakyBucket() {
        assertThat(factory.getOrCreate("leaky", config(AlgorithmType.LEAKY_BUCKET)))
                .isInstanceOf(LeakyBucketRateLimiter.class);
    }

    @Test
    void createsFixedWindow() {
        assertThat(factory.getOrCreate("fixed", config(AlgorithmType.FIXED_WINDOW)))
                .isInstanceOf(FixedWindowRateLimiter.class);
    }

    @Test
    void createsSlidingWindow() {
        assertThat(factory.getOrCreate("sliding", config(AlgorithmType.SLIDING_WINDOW)))
                .isInstanceOf(SlidingWindowRateLimiter.class);
    }

    @Test
    void reusesLimiterByKey() {
        RateLimiter first = factory.getOrCreate("same", config(AlgorithmType.TOKEN_BUCKET));
        RateLimiter second = factory.getOrCreate("same", config(AlgorithmType.TOKEN_BUCKET));

        assertThat(second).isSameAs(first);
    }

    private static RateLimiterConfig config(AlgorithmType algorithm) {
        return RateLimiterConfig.builder(algorithm)
                .capacity(10)
                .ratePerSecond(1.0)
                .window(Duration.ofSeconds(1))
                .build();
    }
}
