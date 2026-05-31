package com.example.ratelimiter.adaptive;

class FakeSystemMetricsProvider implements SystemMetricsProvider {

    private SystemMetrics metrics;

    FakeSystemMetricsProvider(SystemMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public SystemMetrics collect() {
        return metrics;
    }

    void setMetrics(SystemMetrics metrics) {
        this.metrics = metrics;
    }
}
