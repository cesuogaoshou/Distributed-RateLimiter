# Phase 4 Adaptive Rate Limiting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add adaptive rate limiting support that can adjust a local limiter's QPS from runtime system metrics through a PID controller while keeping thresholds bounded and testable.

**Architecture:** Keep adaptive logic in a new `adaptive` package. The PID controller and scheduler are framework-independent and unit-tested with fake metrics and fake adaptive limiters; a Spring-friendly metrics collector is included but no scheduled Spring wiring is required in this phase.

**Tech Stack:** Java 17, Spring Boot 3, JUnit 5, existing `RateLimiterConfig` and local limiter implementations.

---

## File Structure

Create or modify:

```text
README.md
src/main/java/com/example/ratelimiter/adaptive/AdaptiveRateLimiter.java
src/main/java/com/example/ratelimiter/adaptive/AdaptiveRateLimiterScheduler.java
src/main/java/com/example/ratelimiter/adaptive/PIDController.java
src/main/java/com/example/ratelimiter/adaptive/SystemMetrics.java
src/main/java/com/example/ratelimiter/adaptive/SystemMetricsCollector.java
src/main/java/com/example/ratelimiter/adaptive/SystemMetricsProvider.java
src/test/java/com/example/ratelimiter/adaptive/AdaptiveRateLimiterSchedulerTest.java
src/test/java/com/example/ratelimiter/adaptive/FakeAdaptiveRateLimiter.java
src/test/java/com/example/ratelimiter/adaptive/FakeSystemMetricsProvider.java
src/test/java/com/example/ratelimiter/adaptive/PIDControllerTest.java
src/test/java/com/example/ratelimiter/adaptive/SystemMetricsCollectorTest.java
```

Responsibilities:

- `SystemMetrics`: immutable runtime load snapshot.
- `SystemMetricsProvider`: minimal abstraction for testable metrics collection.
- `SystemMetricsCollector`: production JVM metrics collector.
- `PIDController`: framework-free proportional/integral/derivative adjustment calculator.
- `AdaptiveRateLimiter`: interface for limiters whose QPS can be adjusted.
- `AdaptiveRateLimiterScheduler`: applies PID output to registered adaptive limiters with min/max clamping.

## Task 1: Metrics Model and Collector

**Files:**

- Create: `src/main/java/com/example/ratelimiter/adaptive/SystemMetrics.java`
- Create: `src/main/java/com/example/ratelimiter/adaptive/SystemMetricsProvider.java`
- Create: `src/main/java/com/example/ratelimiter/adaptive/SystemMetricsCollector.java`
- Create: `src/test/java/com/example/ratelimiter/adaptive/SystemMetricsCollectorTest.java`

- [ ] **Step 1: Write metrics tests**

Add `SystemMetricsCollectorTest.java`:

```java
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
    void collectorReturnsHeapMetricsAndNonNegativeCpuFallback() {
        SystemMetricsCollector collector = new SystemMetricsCollector(() -> 15);

        SystemMetrics metrics = collector.collect();

        assertThat(metrics.cpuLoad()).isGreaterThanOrEqualTo(-1.0);
        assertThat(metrics.cpuLoad()).isLessThanOrEqualTo(1.0);
        assertThat(metrics.heapUsedBytes()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.heapMaxBytes()).isGreaterThan(0);
        assertThat(metrics.currentQps()).isEqualTo(15);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
mvn test -Dtest=SystemMetricsCollectorTest
```

Expected: compilation fails because adaptive metrics classes do not exist.

- [ ] **Step 3: Add `SystemMetrics`**

Add `SystemMetrics.java`:

```java
package com.example.ratelimiter.adaptive;

public record SystemMetrics(
        double cpuLoad,
        long heapUsedBytes,
        long heapMaxBytes,
        long currentQps
) {
}
```

- [ ] **Step 4: Add provider interface**

Add `SystemMetricsProvider.java`:

```java
package com.example.ratelimiter.adaptive;

public interface SystemMetricsProvider {

    SystemMetrics collect();
}
```

- [ ] **Step 5: Add collector implementation**

Add `SystemMetricsCollector.java`:

```java
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
```

- [ ] **Step 6: Run tests**

Run:

```powershell
mvn test -Dtest=SystemMetricsCollectorTest
```

Expected: metrics tests pass.

- [ ] **Step 7: Commit**

Run:

```powershell
git add src/main/java/com/example/ratelimiter/adaptive src/test/java/com/example/ratelimiter/adaptive/SystemMetricsCollectorTest.java
git commit -m "feat: add adaptive system metrics model"
```

## Task 2: PID Controller

**Files:**

- Create: `src/main/java/com/example/ratelimiter/adaptive/PIDController.java`
- Create: `src/test/java/com/example/ratelimiter/adaptive/PIDControllerTest.java`

- [ ] **Step 1: Write PID tests**

Add `PIDControllerTest.java`:

```java
package com.example.ratelimiter.adaptive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PIDControllerTest {

    @Test
    void returnsPositiveAdjustmentWhenLoadBelowTarget() {
        PIDController controller = new PIDController(0.60, 1.0, 0.0, 0.0);

        double adjustment = controller.calculate(0.40, 1.0);

        assertThat(adjustment).isEqualTo(0.20);
    }

    @Test
    void returnsNegativeAdjustmentWhenLoadAboveTarget() {
        PIDController controller = new PIDController(0.60, 1.0, 0.0, 0.0);

        double adjustment = controller.calculate(0.90, 1.0);

        assertThat(adjustment).isEqualTo(-0.30);
    }

    @Test
    void accumulatesIntegralTerm() {
        PIDController controller = new PIDController(0.60, 0.0, 0.5, 0.0);

        assertThat(controller.calculate(0.50, 1.0)).isEqualTo(0.05);
        assertThat(controller.calculate(0.50, 1.0)).isEqualTo(0.10);
    }

    @Test
    void rejectsNonPositiveDeltaTime() {
        PIDController controller = new PIDController(0.60, 1.0, 0.0, 0.0);

        assertThatThrownBy(() -> controller.calculate(0.50, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deltaSeconds must be positive");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
mvn test -Dtest=PIDControllerTest
```

Expected: compilation fails because `PIDController` does not exist.

- [ ] **Step 3: Add PID controller**

Add `PIDController.java`:

```java
package com.example.ratelimiter.adaptive;

public class PIDController {

    private final double setpoint;
    private final double kp;
    private final double ki;
    private final double kd;
    private double integral;
    private double lastError;
    private boolean hasLastError;

    public PIDController(double setpoint, double kp, double ki, double kd) {
        this.setpoint = setpoint;
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
    }

    public synchronized double calculate(double currentValue, double deltaSeconds) {
        if (deltaSeconds <= 0 || !Double.isFinite(deltaSeconds)) {
            throw new IllegalArgumentException("deltaSeconds must be positive");
        }
        double error = setpoint - currentValue;
        integral += error * deltaSeconds;
        double derivative = hasLastError ? (error - lastError) / deltaSeconds : 0.0;
        lastError = error;
        hasLastError = true;
        return kp * error + ki * integral + kd * derivative;
    }
}
```

- [ ] **Step 4: Run PID tests**

Run:

```powershell
mvn test -Dtest=PIDControllerTest
```

Expected: PID tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add src/main/java/com/example/ratelimiter/adaptive/PIDController.java src/test/java/com/example/ratelimiter/adaptive/PIDControllerTest.java
git commit -m "feat: add pid controller"
```

## Task 3: Adaptive Limiter Contract and Scheduler

**Files:**

- Create: `src/main/java/com/example/ratelimiter/adaptive/AdaptiveRateLimiter.java`
- Create: `src/main/java/com/example/ratelimiter/adaptive/AdaptiveRateLimiterScheduler.java`
- Create: `src/test/java/com/example/ratelimiter/adaptive/FakeAdaptiveRateLimiter.java`
- Create: `src/test/java/com/example/ratelimiter/adaptive/FakeSystemMetricsProvider.java`
- Create: `src/test/java/com/example/ratelimiter/adaptive/AdaptiveRateLimiterSchedulerTest.java`

- [ ] **Step 1: Write scheduler tests**

Add `FakeAdaptiveRateLimiter.java`:

```java
package com.example.ratelimiter.adaptive;

public class FakeAdaptiveRateLimiter implements AdaptiveRateLimiter {

    private final double minQps;
    private final double maxQps;
    private double currentQps;

    public FakeAdaptiveRateLimiter(double currentQps, double minQps, double maxQps) {
        this.currentQps = currentQps;
        this.minQps = minQps;
        this.maxQps = maxQps;
    }

    @Override
    public double currentQps() {
        return currentQps;
    }

    @Override
    public double minQps() {
        return minQps;
    }

    @Override
    public double maxQps() {
        return maxQps;
    }

    @Override
    public void updateQps(double qps) {
        this.currentQps = qps;
    }
}
```

Add `FakeSystemMetricsProvider.java`:

```java
package com.example.ratelimiter.adaptive;

public class FakeSystemMetricsProvider implements SystemMetricsProvider {

    private SystemMetrics metrics;

    public FakeSystemMetricsProvider(SystemMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public SystemMetrics collect() {
        return metrics;
    }

    public void setMetrics(SystemMetrics metrics) {
        this.metrics = metrics;
    }
}
```

Add `AdaptiveRateLimiterSchedulerTest.java`:

```java
package com.example.ratelimiter.adaptive;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(limiter.currentQps()).isEqualTo(120.0);
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

        assertThat(limiter.currentQps()).isEqualTo(80.0);
    }

    @Test
    void clampsLimitToMinAndMax() {
        FakeAdaptiveRateLimiter low = new FakeAdaptiveRateLimiter(100, 90, 200);
        FakeAdaptiveRateLimiter high = new FakeAdaptiveRateLimiter(100, 10, 110);
        FakeSystemMetricsProvider metrics = new FakeSystemMetricsProvider(new SystemMetrics(0.00, 0, 1, 100));

        new AdaptiveRateLimiterScheduler(metrics, new PIDController(0.60, 2.0, 0.0, 0.0), List.of(high))
                .adjust(1.0);
        metrics.setMetrics(new SystemMetrics(1.00, 0, 1, 100));
        new AdaptiveRateLimiterScheduler(metrics, new PIDController(0.60, 2.0, 0.0, 0.0), List.of(low))
                .adjust(1.0);

        assertThat(high.currentQps()).isEqualTo(110.0);
        assertThat(low.currentQps()).isEqualTo(90.0);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
mvn test -Dtest=AdaptiveRateLimiterSchedulerTest
```

Expected: compilation fails because `AdaptiveRateLimiter` and `AdaptiveRateLimiterScheduler` do not exist.

- [ ] **Step 3: Add adaptive limiter contract**

Add `AdaptiveRateLimiter.java`:

```java
package com.example.ratelimiter.adaptive;

public interface AdaptiveRateLimiter {

    double currentQps();

    double minQps();

    double maxQps();

    void updateQps(double qps);
}
```

- [ ] **Step 4: Add scheduler**

Add `AdaptiveRateLimiterScheduler.java`:

```java
package com.example.ratelimiter.adaptive;

import java.util.List;
import java.util.Objects;

public class AdaptiveRateLimiterScheduler {

    private final SystemMetricsProvider metricsProvider;
    private final PIDController pidController;
    private final List<AdaptiveRateLimiter> limiters;

    public AdaptiveRateLimiterScheduler(
            SystemMetricsProvider metricsProvider,
            PIDController pidController,
            List<AdaptiveRateLimiter> limiters) {
        this.metricsProvider = Objects.requireNonNull(metricsProvider, "metricsProvider must not be null");
        this.pidController = Objects.requireNonNull(pidController, "pidController must not be null");
        this.limiters = List.copyOf(limiters);
    }

    public void adjust(double deltaSeconds) {
        SystemMetrics metrics = metricsProvider.collect();
        double adjustment = pidController.calculate(metrics.cpuLoad(), deltaSeconds);
        for (AdaptiveRateLimiter limiter : limiters) {
            double newQps = limiter.currentQps() * (1 + adjustment);
            limiter.updateQps(clamp(newQps, limiter.minQps(), limiter.maxQps()));
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
```

- [ ] **Step 5: Run scheduler tests**

Run:

```powershell
mvn test -Dtest=AdaptiveRateLimiterSchedulerTest
```

Expected: scheduler tests pass.

- [ ] **Step 6: Commit**

Run:

```powershell
git add src/main/java/com/example/ratelimiter/adaptive src/test/java/com/example/ratelimiter/adaptive
git commit -m "feat: add adaptive limiter scheduler"
```

## Task 4: Documentation and Final Verification

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Document adaptive limiting**

Add this section to `README.md`:

```markdown
## 自适应限流

Phase 4 引入自适应限流核心组件：

- `SystemMetricsCollector` 采集 CPU、堆内存和当前 QPS。
- `PIDController` 根据目标 CPU 利用率计算调整比例。
- `AdaptiveRateLimiterScheduler` 将调整比例应用到自适应限流器，并按 min/max QPS 边界裁剪。

PID 调整方向：

| 系统状态 | 调整行为 |
|----------|----------|
| CPU 低于目标值 | 放宽 QPS |
| CPU 高于目标值 | 收紧 QPS |
| 计算结果超过边界 | 限制在 min/max QPS 内 |

当前阶段只提供自适应核心模型和调度器。与具体限流器、Spring 定时任务和配置中心的集成会在后续阶段扩展。
```

- [ ] **Step 2: Run final verification**

Run:

```powershell
mvn test
mvn -Pbenchmark -DskipTests package
java -jar target/benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
git status --short
```

Expected:

- Unit test suite passes.
- Benchmark jar builds.
- JMH smoke benchmark completes.
- Working tree shows only `README.md` before the docs commit, then is clean after commit.

- [ ] **Step 3: Commit**

Run:

```powershell
git add README.md
git commit -m "docs: document adaptive limiter core"
```

- [ ] **Step 4: Push**

Run:

```powershell
git push
```

Expected: local `main` pushes to `origin/main`.

## Self-Review Checklist

Spec coverage:

- `SystemMetrics` model is covered by Task 1.
- Metrics collector is covered by Task 1.
- PID controller is covered by Task 2.
- Adaptive scheduler is covered by Task 3.
- Threshold clamping is covered by Task 3.
- README adaptive notes are covered by Task 4.

Verification:

- Unit tests cover PID direction, integral behavior, scheduler tightening/relaxing, and min/max clamping.
- Final verification keeps existing local, distributed, and benchmark work healthy.

Scope boundaries:

- No Spring `@Scheduled` task in this phase.
- No configuration-center integration in this phase.
- No dashboard integration in this phase.
- No production load experiment numbers are added.
