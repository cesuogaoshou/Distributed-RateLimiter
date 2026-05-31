package com.example.ratelimiter.adaptive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemMetricsCollectorTest {

    @Test
    void systemMetricsStoresRuntimeSnapshot() {
        SystemMetrics metrics = new SystemMetrics(0.75, 128, 512, 42);

        assertThat(metrics.cpuLoad()).isEqualTo(0.75);
        assertThat(metrics.heapUsedBytes()).isEqualTo(128);
        assertThat(metrics.heapMaxBytes()).isEqualTo(512);
        assertThat(metrics.currentQps()).isEqualTo(42);
    }

    @Test
    void collectorReturnsHeapMetricsAndCpuFallbackInValidRange() {
        SystemMetricsCollector collector = new SystemMetricsCollector(() -> 15);

        SystemMetrics metrics = collector.collect();

        assertThat(metrics.cpuLoad()).isGreaterThanOrEqualTo(-1.0);
        assertThat(metrics.cpuLoad()).isLessThanOrEqualTo(1.0);
        assertThat(metrics.heapUsedBytes()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.heapMaxBytes()).isGreaterThan(0);
        assertThat(metrics.currentQps()).isEqualTo(15);
    }
}
