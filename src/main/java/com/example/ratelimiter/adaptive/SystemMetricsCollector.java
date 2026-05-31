package com.example.ratelimiter.adaptive;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Objects;
import java.util.function.LongSupplier;

public class SystemMetricsCollector implements SystemMetricsProvider {

    private final OperatingSystemMXBean osBean;
    private final LongSupplier qpsSupplier;

    public SystemMetricsCollector(LongSupplier qpsSupplier) {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.qpsSupplier = Objects.requireNonNull(qpsSupplier, "qpsSupplier must not be null");
    }

    @Override
    public SystemMetrics collect() {
        Runtime runtime = Runtime.getRuntime();
        long heapMax = runtime.maxMemory();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        return new SystemMetrics(getCpuLoad(), heapUsed, heapMax, qpsSupplier.getAsLong());
    }

    private double getCpuLoad() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            double load = sunBean.getCpuLoad();
            if (Double.isFinite(load)) {
                return Math.max(-1.0, Math.min(1.0, load));
            }
        }
        return -1.0;
    }
}
