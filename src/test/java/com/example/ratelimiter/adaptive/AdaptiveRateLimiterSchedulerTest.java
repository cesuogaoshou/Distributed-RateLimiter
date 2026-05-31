package com.example.ratelimiter.adaptive;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AdaptiveRateLimiterSchedulerTest {

    @Test
    void relaxesLimitWhenCpuBelowTarget() {
        FakeAdaptiveRateLimiter limiter = new FakeAdaptiveRateLimiter(100, 10, 200);
        FakeSystemMetricsProvider metrics = new FakeSystemMetricsProvider(new SystemMetrics(0.40, 0, 1, 100));
        AdaptiveRateLimiterScheduler scheduler = new AdaptiveRateLimiterScheduler(
                metrics,
                new PIDController(0.60, 1.0, 0.0, 0.0),
                List.of(limiter)
        );

        scheduler.adjust(1.0);

        assertThat(limiter.currentQps()).isCloseTo(120.0, within(0.000001));
    }

    @Test
    void tightensLimitWhenCpuAboveTarget() {
        FakeAdaptiveRateLimiter limiter = new FakeAdaptiveRateLimiter(100, 10, 200);
        FakeSystemMetricsProvider metrics = new FakeSystemMetricsProvider(new SystemMetrics(0.80, 0, 1, 100));
        AdaptiveRateLimiterScheduler scheduler = new AdaptiveRateLimiterScheduler(
                metrics,
                new PIDController(0.60, 1.0, 0.0, 0.0),
                List.of(limiter)
        );

        scheduler.adjust(1.0);

        assertThat(limiter.currentQps()).isCloseTo(80.0, within(0.000001));
    }

    @Test
    void clampsLimitToMinAndMax() {
        FakeAdaptiveRateLimiter high = new FakeAdaptiveRateLimiter(100, 10, 110);
        FakeAdaptiveRateLimiter low = new FakeAdaptiveRateLimiter(100, 90, 200);
        FakeSystemMetricsProvider metrics = new FakeSystemMetricsProvider(new SystemMetrics(0.00, 0, 1, 100));

        new AdaptiveRateLimiterScheduler(metrics, new PIDController(0.60, 2.0, 0.0, 0.0), List.of(high))
                .adjust(1.0);
        metrics.setMetrics(new SystemMetrics(1.00, 0, 1, 100));
        new AdaptiveRateLimiterScheduler(metrics, new PIDController(0.60, 2.0, 0.0, 0.0), List.of(low))
                .adjust(1.0);

        assertThat(high.currentQps()).isCloseTo(110.0, within(0.000001));
        assertThat(low.currentQps()).isCloseTo(90.0, within(0.000001));
    }
}
