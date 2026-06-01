package com.example.ratelimiter.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimiterMetricsBinderTest {

    @Test
    void bindToRegistersAggregateRateLimiterGauges() {
        RateLimiterMetricsService service = mock(RateLimiterMetricsService.class);
        when(service.snapshots()).thenReturn(List.of(
                new RateLimiterMetricsSnapshot("orders", 2, 1, 98),
                new RateLimiterMetricsSnapshot("payments", 3, 0, 77)
        ));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new RateLimiterMetricsBinder(service).bindTo(registry);

        assertThat(registry.get("ratelimiter.limiters").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("ratelimiter.requests.allowed").gauge().value()).isEqualTo(5.0);
        assertThat(registry.get("ratelimiter.requests.rejected").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("ratelimiter.permits.available").gauge().value()).isEqualTo(175.0);
    }
}
