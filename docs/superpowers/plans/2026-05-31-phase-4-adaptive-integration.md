# Phase 4 Adaptive Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the adaptive scheduler to a real local `RateLimiter` so PID adjustments can change live QPS through the existing `updateConfig()` API.

**Architecture:** Add a focused adaptive configuration record and a `ConfigurableAdaptiveRateLimiter` wrapper in the existing `adaptive` package. The wrapper implements both `RateLimiter` and `AdaptiveRateLimiter`, delegates traffic decisions to a real local limiter, and updates that limiter's `RateLimiterConfig` when `updateQps()` is called.

**Tech Stack:** Java 17 records/classes, existing local `RateLimiter` implementations, JUnit 5, AssertJ, Maven.

---

## File Structure

Create or modify:

```text
README.md
src/main/java/com/example/ratelimiter/adaptive/AdaptiveRateLimiterConfig.java
src/main/java/com/example/ratelimiter/adaptive/ConfigurableAdaptiveRateLimiter.java
src/test/java/com/example/ratelimiter/adaptive/AdaptiveRateLimiterConfigTest.java
src/test/java/com/example/ratelimiter/adaptive/ConfigurableAdaptiveRateLimiterTest.java
```

Responsibilities:

- `AdaptiveRateLimiterConfig`: immutable adaptive bounds and base limiter config.
- `ConfigurableAdaptiveRateLimiter`: dual interface wrapper that exposes current/min/max QPS and delegates `RateLimiter` behavior to an underlying local limiter.
- `AdaptiveRateLimiterConfigTest`: validates adaptive bounds and generated base config.
- `ConfigurableAdaptiveRateLimiterTest`: proves scheduler PID changes affect a real limiter configuration and acquisition behavior.
- `README.md`: documents how to wrap a local limiter for adaptive scheduling.

## Task 1: Adaptive Config Model

**Files:**

- Create: `src/main/java/com/example/ratelimiter/adaptive/AdaptiveRateLimiterConfig.java`
- Create: `src/test/java/com/example/ratelimiter/adaptive/AdaptiveRateLimiterConfigTest.java`

- [ ] **Step 1: Write config tests**

Add `AdaptiveRateLimiterConfigTest.java`:

```java
package com.example.ratelimiter.adaptive;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.exception.RateLimiterConfigException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptiveRateLimiterConfigTest {

    @Test
    void createsBaseRateLimiterConfigFromAdaptiveSettings() {
        AdaptiveRateLimiterConfig config = new AdaptiveRateLimiterConfig(
                AlgorithmType.TOKEN_BUCKET,
                100,
                20.0,
                10.0,
                80.0,
                Duration.ofSeconds(1)
        );

        RateLimiterConfig base = config.toRateLimiterConfig();

        assertThat(base.algorithm()).isEqualTo(AlgorithmType.TOKEN_BUCKET);
        assertThat(base.capacity()).isEqualTo(100);
        assertThat(base.ratePerSecond()).isEqualTo(20.0);
        assertThat(base.window()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void rejectsInitialQpsBelowMinimum() {
        assertThatThrownBy(() -> new AdaptiveRateLimiterConfig(
                AlgorithmType.TOKEN_BUCKET,
                100,
                5.0,
                10.0,
                80.0,
                Duration.ofSeconds(1)
        ))
                .isInstanceOf(RateLimiterConfigException.class)
                .hasMessageContaining("initialQps must be between minQps and maxQps");
    }

    @Test
    void rejectsInvalidQpsBounds() {
        assertThatThrownBy(() -> new AdaptiveRateLimiterConfig(
                AlgorithmType.TOKEN_BUCKET,
                100,
                20.0,
                80.0,
                10.0,
                Duration.ofSeconds(1)
        ))
                .isInstanceOf(RateLimiterConfigException.class)
                .hasMessageContaining("minQps must be positive and not greater than maxQps");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\adaptive-integration\pom.xml test -Dtest=AdaptiveRateLimiterConfigTest
```

Expected: compilation fails because `AdaptiveRateLimiterConfig` does not exist.

- [ ] **Step 3: Add adaptive config record**

Add `AdaptiveRateLimiterConfig.java`:

```java
package com.example.ratelimiter.adaptive;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.exception.RateLimiterConfigException;

import java.time.Duration;
import java.util.Objects;

public record AdaptiveRateLimiterConfig(
        AlgorithmType algorithm,
        long capacity,
        double initialQps,
        double minQps,
        double maxQps,
        Duration window
) {

    public AdaptiveRateLimiterConfig {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(window, "window must not be null");
        if (minQps <= 0 || !Double.isFinite(minQps) || !Double.isFinite(maxQps) || minQps > maxQps) {
            throw new RateLimiterConfigException("minQps must be positive and not greater than maxQps");
        }
        if (!Double.isFinite(initialQps) || initialQps < minQps || initialQps > maxQps) {
            throw new RateLimiterConfigException("initialQps must be between minQps and maxQps");
        }
    }

    public RateLimiterConfig toRateLimiterConfig() {
        return RateLimiterConfig.builder(algorithm)
                .capacity(capacity)
                .ratePerSecond(initialQps)
                .window(window)
                .build();
    }
}
```

- [ ] **Step 4: Run config tests**

Run:

```powershell
mvn -f .worktrees\adaptive-integration\pom.xml test -Dtest=AdaptiveRateLimiterConfigTest
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

Run:

```powershell
git -C .worktrees\adaptive-integration add src/main/java/com/example/ratelimiter/adaptive/AdaptiveRateLimiterConfig.java src/test/java/com/example/ratelimiter/adaptive/AdaptiveRateLimiterConfigTest.java
git -C .worktrees\adaptive-integration commit -m "feat: add adaptive limiter config"
```

## Task 2: Configurable Adaptive Rate Limiter Wrapper

**Files:**

- Create: `src/main/java/com/example/ratelimiter/adaptive/ConfigurableAdaptiveRateLimiter.java`
- Create: `src/test/java/com/example/ratelimiter/adaptive/ConfigurableAdaptiveRateLimiterTest.java`

- [ ] **Step 1: Write wrapper tests**

Add `ConfigurableAdaptiveRateLimiterTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\adaptive-integration\pom.xml test -Dtest=ConfigurableAdaptiveRateLimiterTest
```

Expected: compilation fails because `ConfigurableAdaptiveRateLimiter` does not exist.

- [ ] **Step 3: Add wrapper implementation**

Add `ConfigurableAdaptiveRateLimiter.java`:

```java
package com.example.ratelimiter.adaptive;

import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.core.RateLimiterFactory;
import com.example.ratelimiter.stats.RateLimiterStats;

import java.util.Objects;

public class ConfigurableAdaptiveRateLimiter implements RateLimiter, AdaptiveRateLimiter {

    private final AdaptiveRateLimiterConfig adaptiveConfig;
    private final RateLimiter delegate;
    private volatile double currentQps;

    public ConfigurableAdaptiveRateLimiter(AdaptiveRateLimiterConfig adaptiveConfig, RateLimiter delegate) {
        this.adaptiveConfig = Objects.requireNonNull(adaptiveConfig, "adaptiveConfig must not be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.currentQps = adaptiveConfig.initialQps();
    }

    public static ConfigurableAdaptiveRateLimiter create(AdaptiveRateLimiterConfig adaptiveConfig) {
        RateLimiter delegate = new RateLimiterFactory().getOrCreate(
                "adaptive:" + adaptiveConfig.algorithm().name(),
                adaptiveConfig.toRateLimiterConfig()
        );
        return new ConfigurableAdaptiveRateLimiter(adaptiveConfig, delegate);
    }

    @Override
    public boolean tryAcquire() {
        return delegate.tryAcquire();
    }

    @Override
    public boolean tryAcquire(int permits) {
        return delegate.tryAcquire(permits);
    }

    @Override
    public long availablePermits() {
        return delegate.availablePermits();
    }

    @Override
    public RateLimiterStats getStats() {
        return delegate.getStats();
    }

    @Override
    public void updateConfig(com.example.ratelimiter.config.RateLimiterConfig config) {
        delegate.updateConfig(config);
        currentQps = config.ratePerSecond();
    }

    @Override
    public double currentQps() {
        return currentQps;
    }

    @Override
    public double minQps() {
        return adaptiveConfig.minQps();
    }

    @Override
    public double maxQps() {
        return adaptiveConfig.maxQps();
    }

    @Override
    public void updateQps(double qps) {
        double boundedQps = Math.max(minQps(), Math.min(maxQps(), qps));
        delegate.updateConfig(adaptiveConfig.toRateLimiterConfig().toBuilder()
                .ratePerSecond(boundedQps)
                .build());
        currentQps = boundedQps;
    }
}
```

- [ ] **Step 4: Run wrapper tests**

Run:

```powershell
mvn -f .worktrees\adaptive-integration\pom.xml test -Dtest=ConfigurableAdaptiveRateLimiterTest
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

Run:

```powershell
git -C .worktrees\adaptive-integration add src/main/java/com/example/ratelimiter/adaptive/ConfigurableAdaptiveRateLimiter.java src/test/java/com/example/ratelimiter/adaptive/ConfigurableAdaptiveRateLimiterTest.java
git -C .worktrees\adaptive-integration commit -m "feat: connect adaptive scheduler to local limiter"
```

## Task 3: README Documentation and Final Verification

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Document adaptive local wrapper**

Append this example to the `## 自适应限流` section:

````markdown
自适应调度器可以包装现有单机限流器并动态调整 QPS：

```java
AdaptiveRateLimiterConfig adaptiveConfig = new AdaptiveRateLimiterConfig(
        AlgorithmType.TOKEN_BUCKET,
        100,
        20.0,
        10.0,
        80.0,
        Duration.ofSeconds(1)
);

ConfigurableAdaptiveRateLimiter limiter = ConfigurableAdaptiveRateLimiter.create(adaptiveConfig);
AdaptiveRateLimiterScheduler scheduler = new AdaptiveRateLimiterScheduler(
        new SystemMetricsCollector(limiter::currentQps),
        new PIDController(0.60, 1.0, 0.0, 0.0),
        List.of(limiter)
);

scheduler.adjust(1.0);
```

这个阶段仍然不引入 Spring `@Scheduled` 自动任务，避免把核心自适应逻辑和框架生命周期耦合在一起。
````

- [ ] **Step 2: Run all tests**

Run:

```powershell
mvn -f .worktrees\adaptive-integration\pom.xml test
```

Expected: all tests pass.

- [ ] **Step 3: Build benchmark jar**

Run:

```powershell
mvn -f .worktrees\adaptive-integration\pom.xml -Pbenchmark -DskipTests package
```

Expected: `BUILD SUCCESS` and `target\benchmarks.jar` exists.

- [ ] **Step 4: Run JMH smoke benchmark**

Run:

```powershell
java -jar .worktrees\adaptive-integration\target\benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
```

Expected: JMH completes and prints one `LocalRateLimiterBenchmark.tokenBucketSingleThread` result row.

- [ ] **Step 5: Commit docs**

Run:

```powershell
git -C .worktrees\adaptive-integration add README.md
git -C .worktrees\adaptive-integration commit -m "docs: document adaptive local limiter integration"
```

## Self-Review Checklist

Spec coverage:

- Phase 4 "Config model for adaptive mode" is covered by Task 1.
- "The adaptive limiter can tighten and relax limits" is covered by Task 2 scheduler integration test.
- "Thresholds are clamped to configured min and max values" is preserved by scheduler and wrapper clamping.
- README documents how the scheduler now connects to a real limiter.

Verification:

- New tests are written before production classes.
- Targeted tests cover config validation, delegated acquire behavior, QPS updates, scheduler integration, and factory-created delegates.
- Final verification runs full unit tests, benchmark packaging, and JMH smoke.

Scope boundaries:

- No Spring `@Scheduled` task in this phase.
- No annotation/AOP integration in this phase.
- No dashboard or Micrometer integration in this phase.
