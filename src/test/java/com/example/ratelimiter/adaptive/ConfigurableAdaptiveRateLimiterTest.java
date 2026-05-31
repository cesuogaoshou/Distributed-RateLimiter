package com.example.ratelimiter.adaptive;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.core.RateLimiterFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurableAdaptiveRateLimiterTest {

    @Test
    void exposesAdaptiveBoundsAndDelegatesAcquire() {
        AdaptiveRateLimiterConfig config = new AdaptiveRateLimiterConfig(
                AlgorithmType.TOKEN_BUCKET,
                2,
                1.0,
                1.0,
                5.0,
                Duration.ofSeconds(1)
        );

        ConfigurableAdaptiveRateLimiter limiter = ConfigurableAdaptiveRateLimiter.create(config);

        assertThat(limiter.currentQps()).isEqualTo(1.0);
        assertThat(limiter.minQps()).isEqualTo(1.0);
        assertThat(limiter.maxQps()).isEqualTo(5.0);
        assertThat(limiter.tryAcquire(2)).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void updateQpsChangesUnderlyingLimiterConfig() throws InterruptedException {
        AdaptiveRateLimiterConfig config = new AdaptiveRateLimiterConfig(
                AlgorithmType.TOKEN_BUCKET,
                10,
                1.0,
                1.0,
                10.0,
                Duration.ofSeconds(1)
        );
        ConfigurableAdaptiveRateLimiter limiter = ConfigurableAdaptiveRateLimiter.create(config);

        limiter.tryAcquire(10);
        assertThat(limiter.tryAcquire()).isFalse();

        limiter.updateQps(10.0);
        Thread.sleep(250);

        assertThat(limiter.currentQps()).isEqualTo(10.0);
        assertThat(limiter.availablePermits()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void schedulerCanRelaxRealLimiterQps() {
        AdaptiveRateLimiterConfig config = new AdaptiveRateLimiterConfig(
                AlgorithmType.TOKEN_BUCKET,
                100,
                20.0,
                10.0,
                80.0,
                Duration.ofSeconds(1)
        );
        ConfigurableAdaptiveRateLimiter limiter = ConfigurableAdaptiveRateLimiter.create(config);
        FakeSystemMetricsProvider metrics = new FakeSystemMetricsProvider(new SystemMetrics(0.40, 0, 1, 20));
        AdaptiveRateLimiterScheduler scheduler = new AdaptiveRateLimiterScheduler(
                metrics,
                new PIDController(0.60, 1.0, 0.0, 0.0),
                List.of(limiter)
        );

        scheduler.adjust(1.0);

        assertThat(limiter.currentQps()).isEqualTo(24.0);
    }

    @Test
    void canWrapFactoryCreatedLimiter() {
        AdaptiveRateLimiterConfig config = new AdaptiveRateLimiterConfig(
                AlgorithmType.FIXED_WINDOW,
                3,
                3.0,
                1.0,
                10.0,
                Duration.ofSeconds(1)
        );
        RateLimiter delegate = new RateLimiterFactory().getOrCreate("adaptive:test", config.toRateLimiterConfig());

        ConfigurableAdaptiveRateLimiter limiter = new ConfigurableAdaptiveRateLimiter(config, delegate);

        assertThat(limiter.tryAcquire(3)).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        limiter.updateQps(5.0);
        assertThat(limiter.currentQps()).isEqualTo(5.0);
    }
}
