package com.example.ratelimiter.metrics;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.core.RateLimiterFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterMetricsServiceTest {

    @Test
    void snapshotsReturnsFactoryStatsSortedByKey() {
        RateLimiterFactory factory = new RateLimiterFactory();
        RateLimiter second = factory.getOrCreate("z-second", config());
        RateLimiter first = factory.getOrCreate("a-first", config());
        first.tryAcquire();
        second.tryAcquire();
        second.tryAcquire();

        RateLimiterMetricsService service = new RateLimiterMetricsService(factory);

        List<RateLimiterMetricsSnapshot> snapshots = service.snapshots();

        assertThat(snapshots).extracting(RateLimiterMetricsSnapshot::key)
                .containsExactly("a-first", "z-second");
        assertThat(snapshots.get(0).allowedRequests()).isEqualTo(1);
        assertThat(snapshots.get(1).allowedRequests()).isEqualTo(2);
    }

    private static RateLimiterConfig config() {
        return RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(100)
                .ratePerSecond(100.0)
                .window(Duration.ofSeconds(1))
                .build();
    }
}
