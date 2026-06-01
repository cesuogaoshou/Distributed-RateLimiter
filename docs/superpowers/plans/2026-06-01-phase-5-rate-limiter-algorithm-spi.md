# Phase 5.5 RateLimiterAlgorithm SPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Java SPI extension point for custom rate limiter algorithms and let `RateLimiterFactory` create custom algorithm limiters by string name.

**Architecture:** Keep built-in algorithms on the existing `AlgorithmType` enum path, and add a separate `customAlgorithm` string path to `RateLimiterConfig` for SPI algorithms. Add `RateLimiterAlgorithm`, `RateLimiterAlgorithmRegistry`, and `RateLimiterAlgorithmLoader` under `spi`, then inject the registry into `RateLimiterFactory` without allowing SPI providers to override built-in enum algorithms.

**Tech Stack:** Java 17, Java `ServiceLoader`, Spring Boot component construction, Maven, JUnit 5, AssertJ.

---

## File Structure

Create or modify:

```text
README.md
src/main/java/com/example/ratelimiter/config/RateLimiterConfig.java
src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java
src/main/java/com/example/ratelimiter/spi/RateLimiterAlgorithm.java
src/main/java/com/example/ratelimiter/spi/RateLimiterAlgorithmLoader.java
src/main/java/com/example/ratelimiter/spi/RateLimiterAlgorithmRegistry.java
src/test/java/com/example/ratelimiter/config/RateLimiterConfigTest.java
src/test/java/com/example/ratelimiter/core/RateLimiterFactoryTest.java
src/test/java/com/example/ratelimiter/spi/RateLimiterAlgorithmLoaderTest.java
src/test/java/com/example/ratelimiter/spi/RateLimiterAlgorithmRegistryTest.java
```

Responsibilities:

- `RateLimiterConfig`: preserves built-in enum config and adds custom algorithm string config.
- `RateLimiterAlgorithm`: public SPI contract for custom algorithms.
- `RateLimiterAlgorithmRegistry`: immutable lookup registry that handles duplicate provider names by priority.
- `RateLimiterAlgorithmLoader`: builds a registry from `ServiceLoader`.
- `RateLimiterFactory`: chooses built-in switch or custom SPI algorithm based on config.
- Tests cover config compatibility, registry behavior, loader behavior, and factory integration.
- `README.md`: documents custom algorithm registration and current Phase 5.5 status.

## Task 1: RateLimiterConfig Custom Algorithm Path

**Files:**

- Modify: `src/main/java/com/example/ratelimiter/config/RateLimiterConfig.java`
- Create: `src/test/java/com/example/ratelimiter/config/RateLimiterConfigTest.java`

- [ ] **Step 1: Write config tests**

Create `src/test/java/com/example/ratelimiter/config/RateLimiterConfigTest.java`:

```java
package com.example.ratelimiter.config;

import com.example.ratelimiter.exception.RateLimiterConfigException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterConfigTest {

    @Test
    void buildsBuiltinAlgorithmConfig() {
        RateLimiterConfig config = RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(20)
                .ratePerSecond(2.0)
                .window(Duration.ofSeconds(2))
                .build();

        assertThat(config.algorithm()).isEqualTo(AlgorithmType.TOKEN_BUCKET);
        assertThat(config.customAlgorithm()).isNull();
        assertThat(config.capacity()).isEqualTo(20);
        assertThat(config.ratePerSecond()).isEqualTo(2.0);
        assertThat(config.window()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void buildsCustomAlgorithmConfig() {
        RateLimiterConfig config = RateLimiterConfig.customAlgorithm("custom-bucket")
                .capacity(10)
                .ratePerSecond(1.0)
                .window(Duration.ofSeconds(1))
                .build();

        assertThat(config.algorithm()).isNull();
        assertThat(config.customAlgorithm()).isEqualTo("custom-bucket");
    }

    @Test
    void trimsCustomAlgorithmName() {
        RateLimiterConfig config = RateLimiterConfig.customAlgorithm("  custom-bucket  ").build();

        assertThat(config.customAlgorithm()).isEqualTo("custom-bucket");
    }

    @Test
    void rejectsBlankCustomAlgorithmName() {
        assertThatThrownBy(() -> RateLimiterConfig.customAlgorithm(" ").build())
                .isInstanceOf(RateLimiterConfigException.class)
                .hasMessageContaining("customAlgorithm must not be blank");
    }

    @Test
    void rejectsNullBuiltinAlgorithm() {
        assertThatThrownBy(() -> RateLimiterConfig.builder(null).build())
                .isInstanceOf(RateLimiterConfigException.class)
                .hasMessageContaining("algorithm must not be null");
    }

    @Test
    void toBuilderPreservesCustomAlgorithmPath() {
        RateLimiterConfig updated = RateLimiterConfig.customAlgorithm("custom-bucket")
                .capacity(10)
                .build()
                .toBuilder()
                .capacity(20)
                .build();

        assertThat(updated.algorithm()).isNull();
        assertThat(updated.customAlgorithm()).isEqualTo("custom-bucket");
        assertThat(updated.capacity()).isEqualTo(20);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\algorithm-spi\pom.xml test -Dtest=RateLimiterConfigTest
```

Expected: compilation fails because `customAlgorithm()` and `RateLimiterConfig.customAlgorithm(String)` do not exist.

- [ ] **Step 3: Update RateLimiterConfig**

Replace `src/main/java/com/example/ratelimiter/config/RateLimiterConfig.java` with:

```java
package com.example.ratelimiter.config;

import com.example.ratelimiter.exception.RateLimiterConfigException;

import java.time.Duration;
import java.util.Objects;

public record RateLimiterConfig(
        AlgorithmType algorithm,
        String customAlgorithm,
        long capacity,
        double ratePerSecond,
        Duration window
) {

    public RateLimiterConfig {
        Objects.requireNonNull(window, "window must not be null");
        if (algorithm == null) {
            if (customAlgorithm == null || customAlgorithm.isBlank()) {
                throw new RateLimiterConfigException("customAlgorithm must not be blank");
            }
            customAlgorithm = customAlgorithm.trim();
        } else if (customAlgorithm != null) {
            throw new RateLimiterConfigException("customAlgorithm must be null for built-in algorithms");
        }
        if (capacity <= 0) {
            throw new RateLimiterConfigException("capacity must be positive");
        }
        if (!Double.isFinite(ratePerSecond) || ratePerSecond < 0) {
            throw new RateLimiterConfigException("ratePerSecond must be finite and not negative");
        }
        if (window.isZero() || window.isNegative()) {
            throw new RateLimiterConfigException("window must be positive");
        }
    }

    public static Builder builder(AlgorithmType algorithm) {
        if (algorithm == null) {
            throw new RateLimiterConfigException("algorithm must not be null");
        }
        return new Builder(algorithm, null);
    }

    public static Builder customAlgorithm(String customAlgorithm) {
        return new Builder(null, customAlgorithm);
    }

    public Builder toBuilder() {
        return new Builder(algorithm, customAlgorithm)
                .capacity(capacity)
                .ratePerSecond(ratePerSecond)
                .window(window);
    }

    public static final class Builder {
        private final AlgorithmType algorithm;
        private final String customAlgorithm;
        private long capacity = 100;
        private double ratePerSecond = 10.0;
        private Duration window = Duration.ofSeconds(1);

        private Builder(AlgorithmType algorithm, String customAlgorithm) {
            this.algorithm = algorithm;
            this.customAlgorithm = customAlgorithm;
        }

        public Builder capacity(long capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder ratePerSecond(double ratePerSecond) {
            this.ratePerSecond = ratePerSecond;
            return this;
        }

        public Builder window(Duration window) {
            this.window = window;
            return this;
        }

        public RateLimiterConfig build() {
            return new RateLimiterConfig(algorithm, customAlgorithm, capacity, ratePerSecond, window);
        }
    }
}
```

- [ ] **Step 4: Run config tests**

Run:

```powershell
mvn -f .worktrees\algorithm-spi\pom.xml test -Dtest=RateLimiterConfigTest
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 5: Run a representative existing algorithm test**

Run:

```powershell
mvn -f .worktrees\algorithm-spi\pom.xml test -Dtest=TokenBucketRateLimiterTest
```

Expected: existing token bucket config usages still compile and pass.

- [ ] **Step 6: Commit**

Run:

```powershell
git -C .worktrees\algorithm-spi add src/main/java/com/example/ratelimiter/config/RateLimiterConfig.java src/test/java/com/example/ratelimiter/config/RateLimiterConfigTest.java
git -C .worktrees\algorithm-spi commit -m "feat: add custom algorithm config path"
```

## Task 2: RateLimiterAlgorithm SPI Registry and Loader

**Files:**

- Create: `src/main/java/com/example/ratelimiter/spi/RateLimiterAlgorithm.java`
- Create: `src/main/java/com/example/ratelimiter/spi/RateLimiterAlgorithmRegistry.java`
- Create: `src/main/java/com/example/ratelimiter/spi/RateLimiterAlgorithmLoader.java`
- Create: `src/test/java/com/example/ratelimiter/spi/RateLimiterAlgorithmRegistryTest.java`
- Create: `src/test/java/com/example/ratelimiter/spi/RateLimiterAlgorithmLoaderTest.java`

- [ ] **Step 1: Write registry tests**

Create `src/test/java/com/example/ratelimiter/spi/RateLimiterAlgorithmRegistryTest.java`:

```java
package com.example.ratelimiter.spi;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterAlgorithmRegistryTest {

    @Test
    void findsAlgorithmByName() {
        RateLimiterAlgorithm algorithm = new TestAlgorithm("custom", 1);
        RateLimiterAlgorithmRegistry registry = new RateLimiterAlgorithmRegistry(List.of(algorithm));

        assertThat(registry.find("custom")).containsSame(algorithm);
    }

    @Test
    void returnsEmptyForMissingAlgorithm() {
        RateLimiterAlgorithmRegistry registry = new RateLimiterAlgorithmRegistry(List.of());

        assertThat(registry.find("missing")).isEmpty();
    }

    @Test
    void keepsHighestPriorityForDuplicateNames() {
        RateLimiterAlgorithm low = new TestAlgorithm("custom", 1);
        RateLimiterAlgorithm high = new TestAlgorithm("custom", 10);

        RateLimiterAlgorithmRegistry registry = new RateLimiterAlgorithmRegistry(List.of(low, high));

        assertThat(registry.find("custom")).containsSame(high);
    }

    @Test
    void rejectsBlankAlgorithmName() {
        assertThatThrownBy(() -> new RateLimiterAlgorithmRegistry(List.of(new TestAlgorithm(" ", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("algorithm name must not be blank");
    }

    private record TestAlgorithm(String name, int priority) implements RateLimiterAlgorithm {

        @Override
        public RateLimiter create(RateLimiterConfig config) {
            return new NoopRateLimiter();
        }
    }

    private static class NoopRateLimiter implements RateLimiter {

        @Override
        public boolean tryAcquire() {
            return true;
        }

        @Override
        public boolean tryAcquire(int permits) {
            return true;
        }

        @Override
        public long availablePermits() {
            return Long.MAX_VALUE;
        }

        @Override
        public RateLimiterStats getStats() {
            return new RateLimiterStats(0, 0, Long.MAX_VALUE);
        }

        @Override
        public void updateConfig(RateLimiterConfig config) {
        }
    }
}
```

- [ ] **Step 2: Write loader tests**

Create `src/test/java/com/example/ratelimiter/spi/RateLimiterAlgorithmLoaderTest.java`:

```java
package com.example.ratelimiter.spi;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterAlgorithmLoaderTest {

    @Test
    void returnsEmptyRegistryWhenNoProvidersExist() {
        RateLimiterAlgorithmRegistry registry = new RateLimiterAlgorithmLoader(List.of()).load();

        assertThat(registry.find("missing")).isEmpty();
    }

    @Test
    void loadsProvidersIntoRegistry() {
        RateLimiterAlgorithm algorithm = new TestAlgorithm("custom", 1);

        RateLimiterAlgorithmRegistry registry = new RateLimiterAlgorithmLoader(List.of(algorithm)).load();

        assertThat(registry.find("custom")).containsSame(algorithm);
    }

    private record TestAlgorithm(String name, int priority) implements RateLimiterAlgorithm {

        @Override
        public RateLimiter create(RateLimiterConfig config) {
            return new NoopRateLimiter();
        }
    }

    private static class NoopRateLimiter implements RateLimiter {

        @Override
        public boolean tryAcquire() {
            return true;
        }

        @Override
        public boolean tryAcquire(int permits) {
            return true;
        }

        @Override
        public long availablePermits() {
            return Long.MAX_VALUE;
        }

        @Override
        public RateLimiterStats getStats() {
            return new RateLimiterStats(0, 0, Long.MAX_VALUE);
        }

        @Override
        public void updateConfig(RateLimiterConfig config) {
        }
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```powershell
mvn -f .worktrees\algorithm-spi\pom.xml test -Dtest=RateLimiterAlgorithmRegistryTest,RateLimiterAlgorithmLoaderTest
```

Expected: compilation fails because `RateLimiterAlgorithm`, `RateLimiterAlgorithmRegistry`, and `RateLimiterAlgorithmLoader` do not exist.

- [ ] **Step 4: Add SPI interface**

Create `src/main/java/com/example/ratelimiter/spi/RateLimiterAlgorithm.java`:

```java
package com.example.ratelimiter.spi;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;

public interface RateLimiterAlgorithm {

    String name();

    RateLimiter create(RateLimiterConfig config);

    default int priority() {
        return 0;
    }
}
```

- [ ] **Step 5: Add registry**

Create `src/main/java/com/example/ratelimiter/spi/RateLimiterAlgorithmRegistry.java`:

```java
package com.example.ratelimiter.spi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class RateLimiterAlgorithmRegistry {

    private final Map<String, RateLimiterAlgorithm> algorithms;

    public RateLimiterAlgorithmRegistry(Iterable<RateLimiterAlgorithm> algorithms) {
        Objects.requireNonNull(algorithms, "algorithms must not be null");
        Map<String, RateLimiterAlgorithm> selected = new HashMap<>();
        for (RateLimiterAlgorithm algorithm : algorithms) {
            Objects.requireNonNull(algorithm, "algorithm must not be null");
            String name = normalizeName(algorithm.name());
            selected.merge(name, algorithm, (existing, candidate) ->
                    candidate.priority() > existing.priority() ? candidate : existing);
        }
        this.algorithms = Collections.unmodifiableMap(selected);
    }

    public Optional<RateLimiterAlgorithm> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(algorithms.get(name.trim()));
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("algorithm name must not be blank");
        }
        return name.trim();
    }
}
```

- [ ] **Step 6: Add loader**

Create `src/main/java/com/example/ratelimiter/spi/RateLimiterAlgorithmLoader.java`:

```java
package com.example.ratelimiter.spi;

import java.util.Objects;
import java.util.ServiceLoader;

public class RateLimiterAlgorithmLoader {

    private final Iterable<RateLimiterAlgorithm> algorithms;

    public RateLimiterAlgorithmLoader() {
        this(ServiceLoader.load(RateLimiterAlgorithm.class));
    }

    public RateLimiterAlgorithmLoader(Iterable<RateLimiterAlgorithm> algorithms) {
        this.algorithms = Objects.requireNonNull(algorithms, "algorithms must not be null");
    }

    public RateLimiterAlgorithmRegistry load() {
        return new RateLimiterAlgorithmRegistry(algorithms);
    }
}
```

- [ ] **Step 7: Run SPI tests**

Run:

```powershell
mvn -f .worktrees\algorithm-spi\pom.xml test -Dtest=RateLimiterAlgorithmRegistryTest,RateLimiterAlgorithmLoaderTest
```

PowerShell note: if comma parsing fails, quote the property:

```powershell
mvn -f .worktrees\algorithm-spi\pom.xml test "-Dtest=RateLimiterAlgorithmRegistryTest,RateLimiterAlgorithmLoaderTest"
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 8: Commit**

Run:

```powershell
git -C .worktrees\algorithm-spi add src/main/java/com/example/ratelimiter/spi/RateLimiterAlgorithm.java src/main/java/com/example/ratelimiter/spi/RateLimiterAlgorithmRegistry.java src/main/java/com/example/ratelimiter/spi/RateLimiterAlgorithmLoader.java src/test/java/com/example/ratelimiter/spi/RateLimiterAlgorithmRegistryTest.java src/test/java/com/example/ratelimiter/spi/RateLimiterAlgorithmLoaderTest.java
git -C .worktrees\algorithm-spi commit -m "feat: add rate limiter algorithm spi"
```

## Task 3: RateLimiterFactory Custom Algorithm Integration

**Files:**

- Modify: `src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java`
- Modify: `src/test/java/com/example/ratelimiter/core/RateLimiterFactoryTest.java`

- [ ] **Step 1: Add factory tests**

Add imports to `RateLimiterFactoryTest`:

```java
import com.example.ratelimiter.spi.RateLimiterAlgorithm;
import com.example.ratelimiter.spi.RateLimiterAlgorithmRegistry;
import com.example.ratelimiter.stats.RateLimiterStats;

import java.util.List;
```

Add these tests to `RateLimiterFactoryTest`:

```java
    @Test
    void createsCustomLimiterFromSpiRegistry() {
        NoopRateLimiter limiter = new NoopRateLimiter();
        RateLimiterFactory customFactory = new RateLimiterFactory(new RateLimiterAlgorithmRegistry(List.of(
                new TestAlgorithm("custom", limiter)
        )));

        RateLimiter created = customFactory.getOrCreate("custom", RateLimiterConfig.customAlgorithm("custom").build());

        assertThat(created).isSameAs(limiter);
    }

    @Test
    void rejectsUnknownCustomAlgorithm() {
        RateLimiterFactory customFactory = new RateLimiterFactory(new RateLimiterAlgorithmRegistry(List.of()));

        assertThatThrownBy(() -> customFactory.getOrCreate("missing", RateLimiterConfig.customAlgorithm("missing").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown custom rate limiter algorithm: missing");
    }

    @Test
    void builtInAlgorithmsDoNotRequireSpiRegistry() {
        RateLimiterFactory customFactory = new RateLimiterFactory(new RateLimiterAlgorithmRegistry(List.of()));

        assertThat(customFactory.getOrCreate("token-with-empty-registry", config(AlgorithmType.TOKEN_BUCKET)))
                .isInstanceOf(TokenBucketRateLimiter.class);
    }
```

Add these helper classes to `RateLimiterFactoryTest`:

```java
    private record TestAlgorithm(String name, RateLimiter limiter) implements RateLimiterAlgorithm {

        @Override
        public RateLimiter create(RateLimiterConfig config) {
            return limiter;
        }
    }

    private static class NoopRateLimiter implements RateLimiter {

        @Override
        public boolean tryAcquire() {
            return true;
        }

        @Override
        public boolean tryAcquire(int permits) {
            return true;
        }

        @Override
        public long availablePermits() {
            return Long.MAX_VALUE;
        }

        @Override
        public RateLimiterStats getStats() {
            return new RateLimiterStats(0, 0, Long.MAX_VALUE);
        }

        @Override
        public void updateConfig(RateLimiterConfig config) {
        }
    }
```

- [ ] **Step 2: Run factory tests to verify they fail**

Run:

```powershell
mvn -f .worktrees\algorithm-spi\pom.xml test -Dtest=RateLimiterFactoryTest
```

Expected: compilation fails because `RateLimiterFactory` has no registry constructor and does not handle `customAlgorithm`.

- [ ] **Step 3: Update RateLimiterFactory**

Replace `src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java` with:

```java
package com.example.ratelimiter.core;

import com.example.ratelimiter.algorithm.FixedWindowRateLimiter;
import com.example.ratelimiter.algorithm.LeakyBucketRateLimiter;
import com.example.ratelimiter.algorithm.SlidingWindowRateLimiter;
import com.example.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.spi.RateLimiterAlgorithmLoader;
import com.example.ratelimiter.spi.RateLimiterAlgorithmRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterFactory {

    private final Map<String, RateLimiter> registry = new ConcurrentHashMap<>();
    private final RateLimiterAlgorithmRegistry algorithmRegistry;

    @Autowired
    public RateLimiterFactory() {
        this(new RateLimiterAlgorithmLoader().load());
    }

    public RateLimiterFactory(RateLimiterAlgorithmRegistry algorithmRegistry) {
        this.algorithmRegistry = Objects.requireNonNull(algorithmRegistry, "algorithmRegistry must not be null");
    }

    public RateLimiter getOrCreate(String key, RateLimiterConfig config) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return registry.computeIfAbsent(key, ignored -> create(config));
    }

    private RateLimiter create(RateLimiterConfig config) {
        if (config.customAlgorithm() != null) {
            return algorithmRegistry.find(config.customAlgorithm())
                    .map(algorithm -> algorithm.create(config))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown custom rate limiter algorithm: " + config.customAlgorithm()));
        }
        return switch (config.algorithm()) {
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(config);
            case LEAKY_BUCKET -> new LeakyBucketRateLimiter(config);
            case FIXED_WINDOW -> new FixedWindowRateLimiter(config);
            case SLIDING_WINDOW -> new SlidingWindowRateLimiter(config);
            case DISTRIBUTED_TOKEN_BUCKET -> throw new IllegalArgumentException(
                    "distributed token bucket requires RedisRateLimiter with a RedisCommandExecutor"
            );
        };
    }
}
```

- [ ] **Step 4: Run factory tests**

Run:

```powershell
mvn -f .worktrees\algorithm-spi\pom.xml test -Dtest=RateLimiterFactoryTest
```

Expected: all factory tests pass.

- [ ] **Step 5: Verify Spring context**

Run:

```powershell
mvn -f .worktrees\algorithm-spi\pom.xml test -Dtest=RateLimiterApplicationTests
```

Expected: Spring context loads successfully with the no-arg `RateLimiterFactory` constructor.

- [ ] **Step 6: Commit**

Run:

```powershell
git -C .worktrees\algorithm-spi add src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java src/test/java/com/example/ratelimiter/core/RateLimiterFactoryTest.java
git -C .worktrees\algorithm-spi commit -m "feat: create custom algorithms from spi"
```

## Task 4: README and Final Verification

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Document RateLimiterAlgorithm SPI**

Update current phase:

```markdown
Phase 5.5: RateLimiterAlgorithm SPI 扩展点。
```

Add completed item:

```markdown
- Java SPI `RateLimiterAlgorithm` 自定义算法扩展点
```

Update next step list:

```markdown
- 补充 Guava/Sentinel 对比入口
- 监控指标和 Dashboard
```

Update annotation limitations:

```markdown
- 暂不支持动态刷新、Redis 分布式模式和自适应模式。
```

Extend the `## SPI 扩展点` section with:

````markdown
### RateLimiterAlgorithm

`RateLimiterAlgorithm` 可用于从外部 jar 注册自定义限流算法。内置算法继续使用 `AlgorithmType`，自定义算法使用字符串名称，不覆盖内置 enum。

自定义算法：

```java
public class WarmupRateLimiterAlgorithm implements RateLimiterAlgorithm {

    @Override
    public String name() {
        return "warmup";
    }

    @Override
    public RateLimiter create(RateLimiterConfig config) {
        return new WarmupRateLimiter(config);
    }

    @Override
    public int priority() {
        return 100;
    }
}
```

使用自定义算法配置：

```java
RateLimiterConfig config = RateLimiterConfig.customAlgorithm("warmup")
        .capacity(100)
        .ratePerSecond(10.0)
        .window(Duration.ofSeconds(1))
        .build();
RateLimiter limiter = new RateLimiterFactory().getOrCreate("order:create", config);
```

注册文件：

```text
META-INF/services/com.example.ratelimiter.spi.RateLimiterAlgorithm
```

文件内容：

```text
com.example.demo.WarmupRateLimiterAlgorithm
```

如果多个自定义算法使用同一个 `name()`，当前会选择 `priority()` 最大的实现。
````

Update the SPI scope list:

```markdown
- 已支持 `RejectHandler`。
- 已支持 `RuleProvider`。
- 已支持 `RateLimiterAlgorithm`。
- 多个同类 SPI 实现同时存在时，选择 `priority()` 最大的实现。
- 自定义算法不覆盖内置 `AlgorithmType`。
```

- [ ] **Step 2: Run all tests**

Run:

```powershell
mvn -f .worktrees\algorithm-spi\pom.xml test
```

Expected: all tests pass.

- [ ] **Step 3: Build benchmark jar**

Run:

```powershell
mvn -f .worktrees\algorithm-spi\pom.xml -Pbenchmark -DskipTests package
```

Expected: `BUILD SUCCESS` and `target\benchmarks.jar` exists.

- [ ] **Step 4: Run JMH smoke benchmark**

Run:

```powershell
java -jar .worktrees\algorithm-spi\target\benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
```

Expected: JMH completes and prints one `LocalRateLimiterBenchmark.tokenBucketSingleThread` result row.

- [ ] **Step 5: Commit docs**

Run:

```powershell
git -C .worktrees\algorithm-spi add README.md
git -C .worktrees\algorithm-spi commit -m "docs: document rate limiter algorithm spi"
```

## Self-Review Checklist

Spec coverage:

- Custom algorithm string config is covered by Task 1.
- `RateLimiterAlgorithm` SPI is covered by Task 2.
- Registry duplicate priority selection is covered by Task 2.
- ServiceLoader registry creation is covered by Task 2.
- Factory built-in compatibility and custom creation are covered by Task 3.
- Spring constructor risk is covered by `RateLimiterApplicationTests` in Task 3.
- README registration docs are covered by Task 4.

Verification:

- Config tests prove built-in compatibility and custom path validation.
- Registry and loader tests prove SPI selection behavior.
- Factory tests prove built-in and custom algorithm creation.
- Full Maven tests, benchmark packaging, and JMH smoke are required before merge.

Scope boundaries:

- No external sample jar.
- No dynamic reload.
- No SPI override of built-in algorithms.
- No Redis distributed factory wiring.
- No dashboard or metrics changes.
