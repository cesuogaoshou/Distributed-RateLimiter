# Phase 5.3 RejectHandler SPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make rate-limit rejection handling pluggable through a Java SPI while preserving the current default `RateLimitException` behavior.

**Architecture:** Add a focused `spi` package containing the `RejectHandler` contract, default implementation, and loader. Update `RateLimitAspect` to delegate rejection to a handler loaded by default through `RejectHandlerLoader`, while keeping Spring's `@Autowired` constructor unambiguous.

**Tech Stack:** Java 17, Java `ServiceLoader`, Spring AOP, Maven, JUnit 5, AssertJ, Mockito.

---

## File Structure

Create or modify:

```text
README.md
src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java
src/main/java/com/example/ratelimiter/spi/DefaultRejectHandler.java
src/main/java/com/example/ratelimiter/spi/RejectHandler.java
src/main/java/com/example/ratelimiter/spi/RejectHandlerLoader.java
src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java
src/test/java/com/example/ratelimiter/spi/DefaultRejectHandlerTest.java
src/test/java/com/example/ratelimiter/spi/RejectHandlerLoaderTest.java
```

Responsibilities:

- `RejectHandler`: public SPI extension contract.
- `DefaultRejectHandler`: default behavior that throws `RateLimitException`.
- `RejectHandlerLoader`: selects the highest-priority ServiceLoader provider, or default handler if none exists.
- `RateLimitAspect`: delegates rejected calls to `RejectHandler`.
- `DefaultRejectHandlerTest`: verifies default exception behavior.
- `RejectHandlerLoaderTest`: verifies fallback and priority selection.
- `RateLimitAspectTest`: keeps existing behavior and adds focused custom handler delegation coverage.
- `README.md`: documents how to implement and register a custom reject handler.

## Task 1: RejectHandler SPI and Default Handler

**Files:**

- Create: `src/main/java/com/example/ratelimiter/spi/RejectHandler.java`
- Create: `src/main/java/com/example/ratelimiter/spi/DefaultRejectHandler.java`
- Create: `src/test/java/com/example/ratelimiter/spi/DefaultRejectHandlerTest.java`

- [ ] **Step 1: Write default handler test**

Create `src/test/java/com/example/ratelimiter/spi/DefaultRejectHandlerTest.java`:

```java
package com.example.ratelimiter.spi;

import com.example.ratelimiter.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultRejectHandlerTest {

    @Test
    void throwsRateLimitExceptionWithRejectedKey() {
        DefaultRejectHandler handler = new DefaultRejectHandler();

        assertThatThrownBy(() -> handler.handle("order:create", null))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Rate limit exceeded for key: order:create");
    }

    @Test
    void hasDefaultPriority() {
        assertThat(new DefaultRejectHandler().priority()).isEqualTo(0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\reject-handler-spi\pom.xml test -Dtest=DefaultRejectHandlerTest
```

Expected: compilation fails because `DefaultRejectHandler` does not exist.

- [ ] **Step 3: Add SPI interface**

Create `src/main/java/com/example/ratelimiter/spi/RejectHandler.java`:

```java
package com.example.ratelimiter.spi;

import com.example.ratelimiter.annotation.RateLimit;

public interface RejectHandler {

    void handle(String key, RateLimit rateLimit);

    default int priority() {
        return 0;
    }
}
```

- [ ] **Step 4: Add default handler**

Create `src/main/java/com/example/ratelimiter/spi/DefaultRejectHandler.java`:

```java
package com.example.ratelimiter.spi;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.exception.RateLimitException;

public class DefaultRejectHandler implements RejectHandler {

    @Override
    public void handle(String key, RateLimit rateLimit) {
        throw new RateLimitException("Rate limit exceeded for key: " + key);
    }
}
```

- [ ] **Step 5: Run default handler tests**

Run:

```powershell
mvn -f .worktrees\reject-handler-spi\pom.xml test -Dtest=DefaultRejectHandlerTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

Run:

```powershell
git -C .worktrees\reject-handler-spi add src/main/java/com/example/ratelimiter/spi/RejectHandler.java src/main/java/com/example/ratelimiter/spi/DefaultRejectHandler.java src/test/java/com/example/ratelimiter/spi/DefaultRejectHandlerTest.java
git -C .worktrees\reject-handler-spi commit -m "feat: add reject handler spi"
```

## Task 2: RejectHandler Loader

**Files:**

- Create: `src/main/java/com/example/ratelimiter/spi/RejectHandlerLoader.java`
- Create: `src/test/java/com/example/ratelimiter/spi/RejectHandlerLoaderTest.java`

- [ ] **Step 1: Write loader tests**

Create `src/test/java/com/example/ratelimiter/spi/RejectHandlerLoaderTest.java`:

```java
package com.example.ratelimiter.spi;

import com.example.ratelimiter.annotation.RateLimit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RejectHandlerLoaderTest {

    @Test
    void fallsBackToDefaultHandlerWhenNoProvidersExist() {
        RejectHandler handler = new RejectHandlerLoader(List.of()).load();

        assertThat(handler).isInstanceOf(DefaultRejectHandler.class);
    }

    @Test
    void choosesHighestPriorityHandler() {
        RejectHandler low = new CapturingRejectHandler(5);
        RejectHandler high = new CapturingRejectHandler(10);

        RejectHandler handler = new RejectHandlerLoader(List.of(low, high)).load();

        assertThat(handler).isSameAs(high);
    }

    private static class CapturingRejectHandler implements RejectHandler {

        private final int priority;

        private CapturingRejectHandler(int priority) {
            this.priority = priority;
        }

        @Override
        public void handle(String key, RateLimit rateLimit) {
        }

        @Override
        public int priority() {
            return priority;
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\reject-handler-spi\pom.xml test -Dtest=RejectHandlerLoaderTest
```

Expected: compilation fails because `RejectHandlerLoader` does not exist.

- [ ] **Step 3: Add loader**

Create `src/main/java/com/example/ratelimiter/spi/RejectHandlerLoader.java`:

```java
package com.example.ratelimiter.spi;

import java.util.Comparator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

public class RejectHandlerLoader {

    private final Iterable<RejectHandler> handlers;

    public RejectHandlerLoader() {
        this(ServiceLoader.load(RejectHandler.class));
    }

    public RejectHandlerLoader(Iterable<RejectHandler> handlers) {
        this.handlers = Objects.requireNonNull(handlers, "handlers must not be null");
    }

    public RejectHandler load() {
        return StreamSupport.stream(handlers.spliterator(), false)
                .max(Comparator.comparingInt(RejectHandler::priority))
                .orElseGet(DefaultRejectHandler::new);
    }
}
```

- [ ] **Step 4: Run loader tests**

Run:

```powershell
mvn -f .worktrees\reject-handler-spi\pom.xml test -Dtest=RejectHandlerLoaderTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

Run:

```powershell
git -C .worktrees\reject-handler-spi add src/main/java/com/example/ratelimiter/spi/RejectHandlerLoader.java src/test/java/com/example/ratelimiter/spi/RejectHandlerLoaderTest.java
git -C .worktrees\reject-handler-spi commit -m "feat: add reject handler loader"
```

## Task 3: Aspect Delegation to RejectHandler

**Files:**

- Modify: `src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java`
- Modify: `src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java`

- [ ] **Step 1: Add custom handler delegation test**

Add imports to `RateLimitAspectTest`:

```java
import com.example.ratelimiter.spi.RejectHandler;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
```

Add this test method to `RateLimitAspectTest`:

```java
    @Test
    void delegatesRejectedCallToCustomRejectHandler() throws Throwable {
        AtomicInteger rejectCount = new AtomicInteger();
        AtomicReference<String> rejectedKey = new AtomicReference<>();
        RejectHandler rejectHandler = (key, rateLimit) -> {
            rejectedKey.set(key);
            rejectCount.incrementAndGet();
        };
        RateLimitAspect aspect = new RateLimitAspect(
                new RateLimiterFactory(),
                new RateLimitRuleProvider(new RateLimitProperties()),
                rejectHandler
        );
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn(TestService.class.getName());
        when(signature.getName()).thenReturn("rejectAfterOne");
        when(joinPoint.proceed()).thenReturn("first");
        RateLimit rateLimit = TestService.class.getMethod("rejectAfterOne").getAnnotation(RateLimit.class);

        assertThat(aspect.around(joinPoint, rateLimit)).isEqualTo("first");
        assertThat(aspect.around(joinPoint, rateLimit)).isNull();

        assertThat(rejectedKey.get()).isEqualTo("aspect:reject-after-one");
        assertThat(rejectCount.get()).isEqualTo(1);
        verify(joinPoint, times(1)).proceed();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\reject-handler-spi\pom.xml test -Dtest=RateLimitAspectTest
```

Expected: compilation fails because `RateLimitAspect` has no constructor accepting `RejectHandler`, or behavior fails because rejection is still thrown directly.

- [ ] **Step 3: Update aspect**

Replace `src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java` with:

```java
package com.example.ratelimiter.aop;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.core.RateLimiterFactory;
import com.example.ratelimiter.rule.RateLimitProperties;
import com.example.ratelimiter.rule.RateLimitRuleProvider;
import com.example.ratelimiter.spi.RejectHandler;
import com.example.ratelimiter.spi.RejectHandlerLoader;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

@Aspect
@Component
public class RateLimitAspect {

    private final RateLimiterFactory rateLimiterFactory;
    private final RateLimitRuleProvider ruleProvider;
    private final RejectHandler rejectHandler;

    public RateLimitAspect(RateLimiterFactory rateLimiterFactory) {
        this(rateLimiterFactory, new RateLimitRuleProvider(new RateLimitProperties()));
    }

    @Autowired
    public RateLimitAspect(RateLimiterFactory rateLimiterFactory, RateLimitRuleProvider ruleProvider) {
        this(rateLimiterFactory, ruleProvider, new RejectHandlerLoader().load());
    }

    public RateLimitAspect(
            RateLimiterFactory rateLimiterFactory,
            RateLimitRuleProvider ruleProvider,
            RejectHandler rejectHandler) {
        this.rateLimiterFactory = Objects.requireNonNull(rateLimiterFactory, "rateLimiterFactory must not be null");
        this.ruleProvider = Objects.requireNonNull(ruleProvider, "ruleProvider must not be null");
        this.rejectHandler = Objects.requireNonNull(rejectHandler, "rejectHandler must not be null");
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = resolveKey(joinPoint, rateLimit);
        ResolvedRule resolvedRule = resolveRule(key, rateLimit);
        RateLimiter limiter = rateLimiterFactory.getOrCreate(key, resolvedRule.config());
        if (!limiter.tryAcquire(resolvedRule.permits())) {
            rejectHandler.handle(key, rateLimit);
            return null;
        }
        return joinPoint.proceed();
    }

    private ResolvedRule resolveRule(String key, RateLimit rateLimit) {
        return ruleProvider.findRule(key)
                .map(rule -> new ResolvedRule(rule.toConfig(), rule.getPermits()))
                .orElseGet(() -> new ResolvedRule(toConfig(rateLimit), rateLimit.permits()));
    }

    private RateLimiterConfig toConfig(RateLimit rateLimit) {
        return RateLimiterConfig.builder(rateLimit.algorithm())
                .capacity(rateLimit.capacity())
                .ratePerSecond(rateLimit.ratePerSecond())
                .window(Duration.ofMillis(rateLimit.windowMillis()))
                .build();
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        if (!rateLimit.key().isBlank()) {
            return rateLimit.key();
        }
        return joinPoint.getSignature().getDeclaringTypeName() + "#" + joinPoint.getSignature().getName();
    }

    private record ResolvedRule(RateLimiterConfig config, int permits) {
    }
}
```

Keep `@Autowired` only on the two-argument Spring constructor. Do not annotate the three-argument test constructor, or Spring may choose the wrong constructor and fail to load the application context.

- [ ] **Step 4: Run aspect tests**

Run:

```powershell
mvn -f .worktrees\reject-handler-spi\pom.xml test -Dtest=RateLimitAspectTest
```

Expected: existing rejection tests still pass and the new custom handler test passes.

- [ ] **Step 5: Commit**

Run:

```powershell
git -C .worktrees\reject-handler-spi add src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java
git -C .worktrees\reject-handler-spi commit -m "feat: delegate rejection to reject handler"
```

## Task 4: README and Final Verification

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Document RejectHandler SPI**

Add this section before `## 文档`:

````markdown
## SPI 扩展点

Phase 5.3 引入第一个 Java SPI 扩展点：`RejectHandler`。默认行为仍然是在限流时抛出 `RateLimitException`。

自定义拒绝处理器：

```java
public class LoggingRejectHandler implements RejectHandler {

    @Override
    public void handle(String key, RateLimit rateLimit) {
        throw new RateLimitException("custom rejected: " + key);
    }

    @Override
    public int priority() {
        return 100;
    }
}
```

注册文件：

```text
META-INF/services/com.example.ratelimiter.spi.RejectHandler
```

文件内容：

```text
com.example.demo.LoggingRejectHandler
```

当前 SPI 范围：

- 已支持 `RejectHandler`。
- 多个实现同时存在时，选择 `priority()` 最大的实现。
- 暂不支持 `RuleProvider` SPI 和 `RateLimiterAlgorithm` SPI。
````

Update current phase:

```markdown
Phase 5.3: Java SPI 拒绝处理扩展点。
```

Add completed item:

```markdown
- Java SPI `RejectHandler` 拒绝处理扩展点
```

Update next step list:

```markdown
- RuleProvider SPI 扩展点
- RateLimiterAlgorithm SPI 扩展点
- 补充 Guava/Sentinel 对比入口
```

Update Spring annotation limitations:

```markdown
- 暂不支持 RuleProvider SPI、RateLimiterAlgorithm SPI、动态刷新、Redis 分布式模式和自适应模式。
```

- [ ] **Step 2: Run all tests**

Run:

```powershell
mvn -f .worktrees\reject-handler-spi\pom.xml test
```

Expected: all tests pass.

- [ ] **Step 3: Build benchmark jar**

Run:

```powershell
mvn -f .worktrees\reject-handler-spi\pom.xml -Pbenchmark -DskipTests package
```

Expected: `BUILD SUCCESS` and `target\benchmarks.jar` exists.

- [ ] **Step 4: Run JMH smoke benchmark**

Run:

```powershell
java -jar .worktrees\reject-handler-spi\target\benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
```

Expected: JMH completes and prints one `LocalRateLimiterBenchmark.tokenBucketSingleThread` result row.

- [ ] **Step 5: Commit docs**

Run:

```powershell
git -C .worktrees\reject-handler-spi add README.md
git -C .worktrees\reject-handler-spi commit -m "docs: document reject handler spi"
```

## Self-Review Checklist

Spec coverage:

- `RejectHandler` SPI is covered by Task 1.
- Default reject handler is covered by Task 1.
- ServiceLoader-based loader is covered by Task 2.
- Highest-priority selection is covered by Task 2.
- Aspect delegation is covered by Task 3.
- Existing default behavior is covered by existing AOP tests and default handler tests.
- README registration docs are covered by Task 4.

Verification:

- Default handler tests cover exception behavior.
- Loader tests cover fallback and priority.
- Aspect tests cover default rejection and custom handler delegation.
- Full Maven tests, benchmark packaging, and JMH smoke are required before merge.

Scope boundaries:

- No `RuleProvider` SPI in this phase.
- No `RateLimiterAlgorithm` SPI in this phase.
- No handler selection by annotation in this phase.
- No handler chain execution in this phase.
- No dynamic SPI reload in this phase.
