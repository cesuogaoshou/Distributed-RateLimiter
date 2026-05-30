# Phase 0 and Phase 1 Local Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Java 17 Maven foundation and the first complete local rate limiter core with four algorithms, common configuration, statistics, factory creation, tests, and starter documentation.

**Architecture:** Use a small single-module Spring Boot 3 Maven project for the first milestone. The core limiter API stays framework-independent under `com.example.ratelimiter`, while Spring Boot is present for later integration phases. Each algorithm implements the same `RateLimiter` interface and is verified with focused JUnit 5 tests.

**Tech Stack:** Java 17 target bytecode, Spring Boot 3.3.x parent, Maven, JUnit 5, AssertJ.

---

## File Structure

Create this structure during the plan:

```text
pom.xml
src/main/java/com/example/ratelimiter/RateLimiterApplication.java
src/main/java/com/example/ratelimiter/algorithm/FixedWindowRateLimiter.java
src/main/java/com/example/ratelimiter/algorithm/LeakyBucketRateLimiter.java
src/main/java/com/example/ratelimiter/algorithm/SlidingWindowRateLimiter.java
src/main/java/com/example/ratelimiter/algorithm/TokenBucketRateLimiter.java
src/main/java/com/example/ratelimiter/config/AlgorithmType.java
src/main/java/com/example/ratelimiter/config/RateLimiterConfig.java
src/main/java/com/example/ratelimiter/core/RateLimiter.java
src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java
src/main/java/com/example/ratelimiter/exception/RateLimiterConfigException.java
src/main/java/com/example/ratelimiter/stats/RateLimiterStats.java
src/test/java/com/example/ratelimiter/RateLimiterApplicationTests.java
src/test/java/com/example/ratelimiter/algorithm/FixedWindowRateLimiterTest.java
src/test/java/com/example/ratelimiter/algorithm/LeakyBucketRateLimiterTest.java
src/test/java/com/example/ratelimiter/algorithm/SlidingWindowRateLimiterTest.java
src/test/java/com/example/ratelimiter/algorithm/TokenBucketRateLimiterTest.java
src/test/java/com/example/ratelimiter/core/RateLimiterFactoryTest.java
src/test/java/com/example/ratelimiter/testsupport/ConcurrentTestSupport.java
```

Responsibilities:

- `RateLimiter`: public API shared by all limiter implementations.
- `RateLimiterConfig`: immutable validated configuration for algorithm construction and hot updates.
- `RateLimiterStats`: immutable snapshot of pass/reject counters and current available permits.
- Algorithm classes: local algorithm state and synchronization only.
- `RateLimiterFactory`: key-based construction and reuse.
- `ConcurrentTestSupport`: deterministic helper for concurrent test execution.

## Task 1: Maven and Spring Boot Foundation

**Files:**

- Create: `pom.xml`
- Create: `src/main/java/com/example/ratelimiter/RateLimiterApplication.java`
- Create: `src/test/java/com/example/ratelimiter/RateLimiterApplicationTests.java`
- Modify: `README.md`

- [ ] **Step 1: Create the Maven build**

Add `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>distributed-ratelimiter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>Distributed RateLimiter</name>
    <description>High-performance distributed rate limiting middleware</description>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.release>17</maven.compiler.release>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Add the application entry point**

Add `src/main/java/com/example/ratelimiter/RateLimiterApplication.java`:

```java
package com.example.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RateLimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateLimiterApplication.class, args);
    }
}
```

- [ ] **Step 3: Add a smoke test**

Add `src/test/java/com/example/ratelimiter/RateLimiterApplicationTests.java`:

```java
package com.example.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RateLimiterApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 4: Run the smoke test**

Run:

```powershell
mvn test
```

Expected: build succeeds with one passing test. If Maven cannot download dependencies, rerun with network permission and do not change source code.

- [ ] **Step 5: Update README commands**

Add this section to `README.md`:

```markdown
## 本地开发

运行测试：

```powershell
mvn test
```

构建项目：

```powershell
mvn package
```
```

- [ ] **Step 6: Commit**

Run:

```powershell
git add pom.xml src README.md
git commit -m "chore: initialize spring boot project"
```

## Task 2: Core API, Config, Stats, and Test Support

**Files:**

- Create: `src/main/java/com/example/ratelimiter/core/RateLimiter.java`
- Create: `src/main/java/com/example/ratelimiter/config/AlgorithmType.java`
- Create: `src/main/java/com/example/ratelimiter/config/RateLimiterConfig.java`
- Create: `src/main/java/com/example/ratelimiter/exception/RateLimiterConfigException.java`
- Create: `src/main/java/com/example/ratelimiter/stats/RateLimiterStats.java`
- Create: `src/test/java/com/example/ratelimiter/testsupport/ConcurrentTestSupport.java`

- [ ] **Step 1: Write config validation tests inside the first algorithm test file**

Create `src/test/java/com/example/ratelimiter/algorithm/TokenBucketRateLimiterTest.java` with the config tests first:

```java
package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.exception.RateLimiterConfigException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBucketRateLimiterTest {

    @Test
    void createsValidTokenBucketConfig() {
        RateLimiterConfig config = RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(10)
                .ratePerSecond(5.0)
                .window(Duration.ofSeconds(1))
                .build();

        assertThat(config.algorithm()).isEqualTo(AlgorithmType.TOKEN_BUCKET);
        assertThat(config.capacity()).isEqualTo(10);
        assertThat(config.ratePerSecond()).isEqualTo(5.0);
        assertThat(config.window()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void rejectsInvalidCapacity() {
        assertThatThrownBy(() -> RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(0)
                .ratePerSecond(1.0)
                .window(Duration.ofSeconds(1))
                .build())
                .isInstanceOf(RateLimiterConfigException.class)
                .hasMessageContaining("capacity must be positive");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn test -Dtest=TokenBucketRateLimiterTest
```

Expected: compilation fails because `AlgorithmType`, `RateLimiterConfig`, and `RateLimiterConfigException` do not exist.

- [ ] **Step 3: Add `AlgorithmType`**

Add `src/main/java/com/example/ratelimiter/config/AlgorithmType.java`:

```java
package com.example.ratelimiter.config;

public enum AlgorithmType {
    TOKEN_BUCKET,
    LEAKY_BUCKET,
    FIXED_WINDOW,
    SLIDING_WINDOW
}
```

- [ ] **Step 4: Add `RateLimiterConfigException`**

Add `src/main/java/com/example/ratelimiter/exception/RateLimiterConfigException.java`:

```java
package com.example.ratelimiter.exception;

public class RateLimiterConfigException extends IllegalArgumentException {

    public RateLimiterConfigException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Add `RateLimiterConfig`**

Add `src/main/java/com/example/ratelimiter/config/RateLimiterConfig.java`:

```java
package com.example.ratelimiter.config;

import com.example.ratelimiter.exception.RateLimiterConfigException;

import java.time.Duration;
import java.util.Objects;

public record RateLimiterConfig(
        AlgorithmType algorithm,
        long capacity,
        double ratePerSecond,
        Duration window
) {

    public RateLimiterConfig {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(window, "window must not be null");
        if (capacity <= 0) {
            throw new RateLimiterConfigException("capacity must be positive");
        }
        if (ratePerSecond < 0) {
            throw new RateLimiterConfigException("ratePerSecond must not be negative");
        }
        if (window.isZero() || window.isNegative()) {
            throw new RateLimiterConfigException("window must be positive");
        }
    }

    public static Builder builder(AlgorithmType algorithm) {
        return new Builder(algorithm);
    }

    public Builder toBuilder() {
        return new Builder(algorithm)
                .capacity(capacity)
                .ratePerSecond(ratePerSecond)
                .window(window);
    }

    public static final class Builder {
        private final AlgorithmType algorithm;
        private long capacity = 100;
        private double ratePerSecond = 10.0;
        private Duration window = Duration.ofSeconds(1);

        private Builder(AlgorithmType algorithm) {
            this.algorithm = algorithm;
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
            return new RateLimiterConfig(algorithm, capacity, ratePerSecond, window);
        }
    }
}
```

- [ ] **Step 6: Add `RateLimiterStats`**

Add `src/main/java/com/example/ratelimiter/stats/RateLimiterStats.java`:

```java
package com.example.ratelimiter.stats;

public record RateLimiterStats(
        long allowedRequests,
        long rejectedRequests,
        long availablePermits
) {
}
```

- [ ] **Step 7: Add `RateLimiter` interface**

Add `src/main/java/com/example/ratelimiter/core/RateLimiter.java`:

```java
package com.example.ratelimiter.core;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.stats.RateLimiterStats;

public interface RateLimiter {

    boolean tryAcquire();

    boolean tryAcquire(int permits);

    long availablePermits();

    RateLimiterStats getStats();

    void updateConfig(RateLimiterConfig config);
}
```

- [ ] **Step 8: Add concurrent test helper**

Add `src/test/java/com/example/ratelimiter/testsupport/ConcurrentTestSupport.java`:

```java
package com.example.ratelimiter.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ConcurrentTestSupport {

    private ConcurrentTestSupport() {
    }

    public static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return task.call();
            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(5, TimeUnit.SECONDS));
        }
        executor.shutdownNow();
        return results;
    }
}
```

- [ ] **Step 9: Run config tests**

Run:

```powershell
mvn test -Dtest=TokenBucketRateLimiterTest
```

Expected: tests pass.

- [ ] **Step 10: Commit**

Run:

```powershell
git add src
git commit -m "feat: add rate limiter core models"
```

## Task 3: Token Bucket Rate Limiter

**Files:**

- Modify: `src/test/java/com/example/ratelimiter/algorithm/TokenBucketRateLimiterTest.java`
- Create: `src/main/java/com/example/ratelimiter/algorithm/TokenBucketRateLimiter.java`

- [ ] **Step 1: Add failing token bucket behavior tests**

Append these tests to `TokenBucketRateLimiterTest`:

```java
    @Test
    void allowsBurstUpToCapacity() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(3)
                .ratePerSecond(1.0)
                .window(Duration.ofSeconds(1))
                .build());

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        assertThat(limiter.availablePermits()).isZero();
    }

    @Test
    void supportsBulkPermits() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(5)
                .ratePerSecond(1.0)
                .window(Duration.ofSeconds(1))
                .build());

        assertThat(limiter.tryAcquire(3)).isTrue();
        assertThat(limiter.availablePermits()).isEqualTo(2);
        assertThat(limiter.tryAcquire(3)).isFalse();
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(2)
                .ratePerSecond(20.0)
                .window(Duration.ofSeconds(1))
                .build());

        assertThat(limiter.tryAcquire(2)).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        Thread.sleep(80);

        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void updatesStats() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(1)
                .ratePerSecond(0.0)
                .window(Duration.ofSeconds(1))
                .build());

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        assertThat(limiter.getStats().allowedRequests()).isEqualTo(1);
        assertThat(limiter.getStats().rejectedRequests()).isEqualTo(1);
    }

    @Test
    void doesNotOverIssueUnderConcurrency() throws Exception {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(5)
                .ratePerSecond(0.0)
                .window(Duration.ofSeconds(1))
                .build());

        long allowed = ConcurrentTestSupport.runConcurrently(20, limiter::tryAcquire)
                .stream()
                .filter(Boolean::booleanValue)
                .count();

        assertThat(allowed).isEqualTo(5);
        assertThat(limiter.availablePermits()).isZero();
    }

    @Test
    void updateConfigChangesCapacity() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(1)
                .ratePerSecond(0.0)
                .window(Duration.ofSeconds(1))
                .build());

        assertThat(limiter.tryAcquire()).isTrue();
        limiter.updateConfig(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(3)
                .ratePerSecond(0.0)
                .window(Duration.ofSeconds(1))
                .build());

        assertThat(limiter.availablePermits()).isEqualTo(3);
    }
```

Also add this import:

```java
import com.example.ratelimiter.testsupport.ConcurrentTestSupport;
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
mvn test -Dtest=TokenBucketRateLimiterTest
```

Expected: compilation fails because `TokenBucketRateLimiter` does not exist.

- [ ] **Step 3: Implement token bucket**

Add `src/main/java/com/example/ratelimiter/algorithm/TokenBucketRateLimiter.java`:

```java
package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;

public class TokenBucketRateLimiter implements RateLimiter {

    private RateLimiterConfig config;
    private double currentTokens;
    private long lastRefillNanos;
    private long allowedRequests;
    private long rejectedRequests;

    public TokenBucketRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.currentTokens = config.capacity();
        this.lastRefillNanos = System.nanoTime();
    }

    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    @Override
    public synchronized boolean tryAcquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive");
        }
        refill();
        if (currentTokens >= permits) {
            currentTokens -= permits;
            allowedRequests++;
            return true;
        }
        rejectedRequests++;
        return false;
    }

    @Override
    public synchronized long availablePermits() {
        refill();
        return (long) currentTokens;
    }

    @Override
    public synchronized RateLimiterStats getStats() {
        refill();
        return new RateLimiterStats(allowedRequests, rejectedRequests, (long) currentTokens);
    }

    @Override
    public synchronized void updateConfig(RateLimiterConfig config) {
        this.config = config;
        this.currentTokens = config.capacity();
        this.lastRefillNanos = System.nanoTime();
    }

    private void refill() {
        if (config.ratePerSecond() <= 0) {
            return;
        }
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        if (elapsedSeconds <= 0) {
            return;
        }
        currentTokens = Math.min(config.capacity(), currentTokens + elapsedSeconds * config.ratePerSecond());
        lastRefillNanos = now;
    }
}
```

- [ ] **Step 4: Run token bucket tests**

Run:

```powershell
mvn test -Dtest=TokenBucketRateLimiterTest
```

Expected: all token bucket tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add src
git commit -m "feat: implement token bucket limiter"
```

## Task 4: Leaky Bucket Rate Limiter

**Files:**

- Create: `src/test/java/com/example/ratelimiter/algorithm/LeakyBucketRateLimiterTest.java`
- Create: `src/main/java/com/example/ratelimiter/algorithm/LeakyBucketRateLimiter.java`

- [ ] **Step 1: Add failing leaky bucket tests**

Add `LeakyBucketRateLimiterTest.java`:

```java
package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.testsupport.ConcurrentTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LeakyBucketRateLimiterTest {

    @Test
    void acceptsUntilBucketIsFull() {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(config(3, 0.0));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void drainsOverTime() throws InterruptedException {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(config(1, 20.0));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        Thread.sleep(80);
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void supportsBulkPermits() {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(config(5, 0.0));

        assertThat(limiter.tryAcquire(4)).isTrue();
        assertThat(limiter.tryAcquire(2)).isFalse();
    }

    @Test
    void doesNotOverfillUnderConcurrency() throws Exception {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(config(4, 0.0));

        long allowed = ConcurrentTestSupport.runConcurrently(20, limiter::tryAcquire)
                .stream()
                .filter(Boolean::booleanValue)
                .count();

        assertThat(allowed).isEqualTo(4);
    }

    @Test
    void updatesStats() {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(config(1, 0.0));

        limiter.tryAcquire();
        limiter.tryAcquire();

        assertThat(limiter.getStats().allowedRequests()).isEqualTo(1);
        assertThat(limiter.getStats().rejectedRequests()).isEqualTo(1);
    }

    private static RateLimiterConfig config(long capacity, double drainRate) {
        return RateLimiterConfig.builder(AlgorithmType.LEAKY_BUCKET)
                .capacity(capacity)
                .ratePerSecond(drainRate)
                .window(Duration.ofSeconds(1))
                .build();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
mvn test -Dtest=LeakyBucketRateLimiterTest
```

Expected: compilation fails because `LeakyBucketRateLimiter` does not exist.

- [ ] **Step 3: Implement leaky bucket**

Add `LeakyBucketRateLimiter.java`:

```java
package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;

public class LeakyBucketRateLimiter implements RateLimiter {

    private RateLimiterConfig config;
    private double waterLevel;
    private long lastDrainNanos;
    private long allowedRequests;
    private long rejectedRequests;

    public LeakyBucketRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.lastDrainNanos = System.nanoTime();
    }

    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    @Override
    public synchronized boolean tryAcquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive");
        }
        drain();
        if (waterLevel + permits <= config.capacity()) {
            waterLevel += permits;
            allowedRequests++;
            return true;
        }
        rejectedRequests++;
        return false;
    }

    @Override
    public synchronized long availablePermits() {
        drain();
        return Math.max(0, config.capacity() - (long) Math.ceil(waterLevel));
    }

    @Override
    public synchronized RateLimiterStats getStats() {
        drain();
        return new RateLimiterStats(allowedRequests, rejectedRequests, availablePermits());
    }

    @Override
    public synchronized void updateConfig(RateLimiterConfig config) {
        this.config = config;
        this.waterLevel = Math.min(waterLevel, config.capacity());
        this.lastDrainNanos = System.nanoTime();
    }

    private void drain() {
        if (config.ratePerSecond() <= 0) {
            return;
        }
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastDrainNanos) / 1_000_000_000.0;
        if (elapsedSeconds <= 0) {
            return;
        }
        waterLevel = Math.max(0, waterLevel - elapsedSeconds * config.ratePerSecond());
        lastDrainNanos = now;
    }
}
```

- [ ] **Step 4: Run leaky bucket tests**

Run:

```powershell
mvn test -Dtest=LeakyBucketRateLimiterTest
```

Expected: all leaky bucket tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add src
git commit -m "feat: implement leaky bucket limiter"
```

## Task 5: Fixed Window Rate Limiter

**Files:**

- Create: `src/test/java/com/example/ratelimiter/algorithm/FixedWindowRateLimiterTest.java`
- Create: `src/main/java/com/example/ratelimiter/algorithm/FixedWindowRateLimiter.java`

- [ ] **Step 1: Add failing fixed window tests**

Add `FixedWindowRateLimiterTest.java`:

```java
package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.testsupport.ConcurrentTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    @Test
    void rejectsAfterWindowCapacity() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(config(2, Duration.ofMillis(200)));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void resetsAfterWindowExpires() throws InterruptedException {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(config(1, Duration.ofMillis(50)));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        Thread.sleep(80);
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void supportsBulkPermits() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(config(5, Duration.ofSeconds(1)));

        assertThat(limiter.tryAcquire(3)).isTrue();
        assertThat(limiter.tryAcquire(3)).isFalse();
    }

    @Test
    void doesNotOverAllowUnderConcurrency() throws Exception {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(config(3, Duration.ofSeconds(1)));

        long allowed = ConcurrentTestSupport.runConcurrently(20, limiter::tryAcquire)
                .stream()
                .filter(Boolean::booleanValue)
                .count();

        assertThat(allowed).isEqualTo(3);
    }

    private static RateLimiterConfig config(long capacity, Duration window) {
        return RateLimiterConfig.builder(AlgorithmType.FIXED_WINDOW)
                .capacity(capacity)
                .ratePerSecond(0.0)
                .window(window)
                .build();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
mvn test -Dtest=FixedWindowRateLimiterTest
```

Expected: compilation fails because `FixedWindowRateLimiter` does not exist.

- [ ] **Step 3: Implement fixed window**

Add `FixedWindowRateLimiter.java`:

```java
package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;

import java.util.concurrent.atomic.AtomicLong;

public class FixedWindowRateLimiter implements RateLimiter {

    private volatile RateLimiterConfig config;
    private final AtomicLong counter = new AtomicLong();
    private volatile long windowStartMillis;
    private final AtomicLong allowedRequests = new AtomicLong();
    private final AtomicLong rejectedRequests = new AtomicLong();

    public FixedWindowRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.windowStartMillis = System.currentTimeMillis();
    }

    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    @Override
    public boolean tryAcquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive");
        }
        refreshWindowIfNeeded();
        while (true) {
            long current = counter.get();
            if (current + permits > config.capacity()) {
                rejectedRequests.incrementAndGet();
                return false;
            }
            if (counter.compareAndSet(current, current + permits)) {
                allowedRequests.incrementAndGet();
                return true;
            }
        }
    }

    @Override
    public long availablePermits() {
        refreshWindowIfNeeded();
        return Math.max(0, config.capacity() - counter.get());
    }

    @Override
    public RateLimiterStats getStats() {
        return new RateLimiterStats(allowedRequests.get(), rejectedRequests.get(), availablePermits());
    }

    @Override
    public synchronized void updateConfig(RateLimiterConfig config) {
        this.config = config;
        this.counter.set(0);
        this.windowStartMillis = System.currentTimeMillis();
    }

    private void refreshWindowIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - windowStartMillis < config.window().toMillis()) {
            return;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now - windowStartMillis >= config.window().toMillis()) {
                counter.set(0);
                windowStartMillis = now;
            }
        }
    }
}
```

- [ ] **Step 4: Run fixed window tests**

Run:

```powershell
mvn test -Dtest=FixedWindowRateLimiterTest
```

Expected: all fixed window tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add src
git commit -m "feat: implement fixed window limiter"
```

## Task 6: Sliding Window Rate Limiter

**Files:**

- Create: `src/test/java/com/example/ratelimiter/algorithm/SlidingWindowRateLimiterTest.java`
- Create: `src/main/java/com/example/ratelimiter/algorithm/SlidingWindowRateLimiter.java`

- [ ] **Step 1: Add failing sliding window tests**

Add `SlidingWindowRateLimiterTest.java`:

```java
package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.testsupport.ConcurrentTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowRateLimiterTest {

    @Test
    void rejectsWhenEventsInsideWindowReachCapacity() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(config(2, Duration.ofMillis(200)));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void evictsExpiredEvents() throws InterruptedException {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(config(1, Duration.ofMillis(50)));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        Thread.sleep(80);
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void supportsBulkPermits() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(config(4, Duration.ofSeconds(1)));

        assertThat(limiter.tryAcquire(3)).isTrue();
        assertThat(limiter.tryAcquire(2)).isFalse();
    }

    @Test
    void doesNotOverAllowUnderConcurrency() throws Exception {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(config(3, Duration.ofSeconds(1)));

        long allowed = ConcurrentTestSupport.runConcurrently(20, limiter::tryAcquire)
                .stream()
                .filter(Boolean::booleanValue)
                .count();

        assertThat(allowed).isEqualTo(3);
    }

    private static RateLimiterConfig config(long capacity, Duration window) {
        return RateLimiterConfig.builder(AlgorithmType.SLIDING_WINDOW)
                .capacity(capacity)
                .ratePerSecond(0.0)
                .window(window)
                .build();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
mvn test -Dtest=SlidingWindowRateLimiterTest
```

Expected: compilation fails because `SlidingWindowRateLimiter` does not exist.

- [ ] **Step 3: Implement sliding window**

Add `SlidingWindowRateLimiter.java`:

```java
package com.example.ratelimiter.algorithm;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;

import java.util.ArrayDeque;
import java.util.Deque;

public class SlidingWindowRateLimiter implements RateLimiter {

    private RateLimiterConfig config;
    private final Deque<Long> timestamps = new ArrayDeque<>();
    private long allowedRequests;
    private long rejectedRequests;

    public SlidingWindowRateLimiter(RateLimiterConfig config) {
        this.config = config;
    }

    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    @Override
    public synchronized boolean tryAcquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive");
        }
        long now = System.currentTimeMillis();
        evictExpired(now);
        if (timestamps.size() + permits > config.capacity()) {
            rejectedRequests++;
            return false;
        }
        for (int i = 0; i < permits; i++) {
            timestamps.addLast(now);
        }
        allowedRequests++;
        return true;
    }

    @Override
    public synchronized long availablePermits() {
        evictExpired(System.currentTimeMillis());
        return Math.max(0, config.capacity() - timestamps.size());
    }

    @Override
    public synchronized RateLimiterStats getStats() {
        return new RateLimiterStats(allowedRequests, rejectedRequests, availablePermits());
    }

    @Override
    public synchronized void updateConfig(RateLimiterConfig config) {
        this.config = config;
        timestamps.clear();
    }

    private void evictExpired(long now) {
        long windowMillis = config.window().toMillis();
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMillis) {
            timestamps.removeFirst();
        }
    }
}
```

- [ ] **Step 4: Run sliding window tests**

Run:

```powershell
mvn test -Dtest=SlidingWindowRateLimiterTest
```

Expected: all sliding window tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add src
git commit -m "feat: implement sliding window limiter"
```

## Task 7: Rate Limiter Factory and Cross-Algorithm Tests

**Files:**

- Create: `src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java`
- Create: `src/test/java/com/example/ratelimiter/core/RateLimiterFactoryTest.java`

- [ ] **Step 1: Add failing factory tests**

Add `RateLimiterFactoryTest.java`:

```java
package com.example.ratelimiter.core;

import com.example.ratelimiter.algorithm.FixedWindowRateLimiter;
import com.example.ratelimiter.algorithm.LeakyBucketRateLimiter;
import com.example.ratelimiter.algorithm.SlidingWindowRateLimiter;
import com.example.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterFactoryTest {

    private final RateLimiterFactory factory = new RateLimiterFactory();

    @Test
    void createsTokenBucket() {
        assertThat(factory.getOrCreate("token", config(AlgorithmType.TOKEN_BUCKET)))
                .isInstanceOf(TokenBucketRateLimiter.class);
    }

    @Test
    void createsLeakyBucket() {
        assertThat(factory.getOrCreate("leaky", config(AlgorithmType.LEAKY_BUCKET)))
                .isInstanceOf(LeakyBucketRateLimiter.class);
    }

    @Test
    void createsFixedWindow() {
        assertThat(factory.getOrCreate("fixed", config(AlgorithmType.FIXED_WINDOW)))
                .isInstanceOf(FixedWindowRateLimiter.class);
    }

    @Test
    void createsSlidingWindow() {
        assertThat(factory.getOrCreate("sliding", config(AlgorithmType.SLIDING_WINDOW)))
                .isInstanceOf(SlidingWindowRateLimiter.class);
    }

    @Test
    void reusesLimiterByKey() {
        RateLimiter first = factory.getOrCreate("same", config(AlgorithmType.TOKEN_BUCKET));
        RateLimiter second = factory.getOrCreate("same", config(AlgorithmType.TOKEN_BUCKET));

        assertThat(second).isSameAs(first);
    }

    private static RateLimiterConfig config(AlgorithmType algorithm) {
        return RateLimiterConfig.builder(algorithm)
                .capacity(10)
                .ratePerSecond(1.0)
                .window(Duration.ofSeconds(1))
                .build();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
mvn test -Dtest=RateLimiterFactoryTest
```

Expected: compilation fails because `RateLimiterFactory` does not exist.

- [ ] **Step 3: Implement factory**

Add `RateLimiterFactory.java`:

```java
package com.example.ratelimiter.core;

import com.example.ratelimiter.algorithm.FixedWindowRateLimiter;
import com.example.ratelimiter.algorithm.LeakyBucketRateLimiter;
import com.example.ratelimiter.algorithm.SlidingWindowRateLimiter;
import com.example.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.example.ratelimiter.config.RateLimiterConfig;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiterFactory {

    private final Map<String, RateLimiter> registry = new ConcurrentHashMap<>();

    public RateLimiter getOrCreate(String key, RateLimiterConfig config) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return registry.computeIfAbsent(key, ignored -> create(config));
    }

    private RateLimiter create(RateLimiterConfig config) {
        return switch (config.algorithm()) {
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(config);
            case LEAKY_BUCKET -> new LeakyBucketRateLimiter(config);
            case FIXED_WINDOW -> new FixedWindowRateLimiter(config);
            case SLIDING_WINDOW -> new SlidingWindowRateLimiter(config);
        };
    }
}
```

- [ ] **Step 4: Run factory tests**

Run:

```powershell
mvn test -Dtest=RateLimiterFactoryTest
```

Expected: all factory tests pass.

- [ ] **Step 5: Run full test suite**

Run:

```powershell
mvn test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

Run:

```powershell
git add src
git commit -m "feat: add rate limiter factory"
```

## Task 8: README Phase 1 Documentation and Verification

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Update README with Phase 1 usage example**

Add this section:

```markdown
## 单机限流示例

```java
RateLimiterConfig config = RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
        .capacity(100)
        .ratePerSecond(10.0)
        .window(Duration.ofSeconds(1))
        .build();

RateLimiter limiter = new TokenBucketRateLimiter(config);

if (limiter.tryAcquire()) {
    // handle request
}
```

## 单机算法

| 算法 | 特点 | 当前实现 |
|------|------|----------|
| Token Bucket | 允许短时突发，按固定速率补充令牌 | `synchronized` 保证 refill + acquire 原子性 |
| Leaky Bucket | 平滑流量，桶满拒绝 | `synchronized` 保证 drain + acquire 原子性 |
| Fixed Window | 实现简单，窗口边界可能突刺 | `AtomicLong` + 窗口刷新 |
| Sliding Window | 更精确地限制最近时间窗口 | `synchronized` + 时间戳队列 |
```

- [ ] **Step 2: Run final verification**

Run:

```powershell
mvn test
git status --short
```

Expected:

- Maven test suite passes.
- `git status --short` shows only `README.md` modified.

- [ ] **Step 3: Commit docs**

Run:

```powershell
git add README.md
git commit -m "docs: document local rate limiter core"
```

- [ ] **Step 4: Push commits**

Run:

```powershell
git push
```

Expected: `main` pushes to `origin/main`.

## Self-Review Checklist

Spec coverage:

- Phase 0 project initialization is covered by Task 1.
- Core API and models are covered by Task 2.
- Token bucket is covered by Task 3.
- Leaky bucket is covered by Task 4.
- Fixed window is covered by Task 5.
- Sliding window is covered by Task 6.
- Factory and cross-algorithm construction are covered by Task 7.
- README starter documentation is covered by Task 8.

Deferred by design:

- Redis and Lua are Phase 3.
- JMH is Phase 2.
- Adaptive PID limiting is Phase 4.
- Spring AOP, SPI, and dashboard are later phases.

Verification:

- Every implementation task has a failing-test step before code.
- Every task has an explicit test command and expected result.
- Every task ends with a focused commit.
