# Phase 5.1 RateLimit AOP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal Spring annotation integration so methods annotated with `@RateLimit` are protected by the existing local rate limiter engine.

**Architecture:** Keep the public annotation in a small `annotation` package, the Spring AOP interceptor in `aop`, and rejection behavior in the existing `exception` package. The aspect converts annotation attributes into `RateLimiterConfig`, obtains a limiter from `RateLimiterFactory`, and fast-fails with `RateLimitException` when permits cannot be acquired.

**Tech Stack:** Java 17, Spring Boot 3, Spring AOP, Maven, JUnit 5, AssertJ.

---

## File Structure

Create or modify:

```text
pom.xml
README.md
src/main/java/com/example/ratelimiter/annotation/RateLimit.java
src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java
src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java
src/main/java/com/example/ratelimiter/exception/RateLimitException.java
src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java
```

Responsibilities:

- `pom.xml`: add Spring AOP dependency.
- `RateLimit`: method-level annotation API.
- `RateLimitException`: fast-fail exception for rejected method calls.
- `RateLimiterFactory`: become a Spring component while retaining direct construction support.
- `RateLimitAspect`: resolve annotation settings, acquire permits, proceed or reject.
- `RateLimitAspectTest`: Spring AOP integration test covering pass, reject, unannotated method, and blank-key behavior.
- `README.md`: document annotation usage and current limitations.

## Task 1: Annotation, Exception, and Spring Dependency

**Files:**

- Modify: `pom.xml`
- Create: `src/main/java/com/example/ratelimiter/annotation/RateLimit.java`
- Create: `src/main/java/com/example/ratelimiter/exception/RateLimitException.java`

- [ ] **Step 1: Write a compile-focused annotation test**

Create `src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java` with only a compile-focused annotation shape test first:

```java
package com.example.ratelimiter.aop;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.config.AlgorithmType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitAspectTest {

    @Test
    void rateLimitAnnotationExposesExpectedDefaults() throws NoSuchMethodException {
        Method method = AnnotatedService.class.getDeclaredMethod("limited");
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        assertThat(rateLimit.key()).isEmpty();
        assertThat(rateLimit.algorithm()).isEqualTo(AlgorithmType.TOKEN_BUCKET);
        assertThat(rateLimit.capacity()).isEqualTo(100);
        assertThat(rateLimit.ratePerSecond()).isEqualTo(10.0);
        assertThat(rateLimit.windowMillis()).isEqualTo(1000);
        assertThat(rateLimit.permits()).isEqualTo(1);
    }

    static class AnnotatedService {

        @RateLimit
        String limited() {
            return "ok";
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\rate-limit-aop\pom.xml test -Dtest=RateLimitAspectTest
```

Expected: compilation fails because `RateLimit` does not exist.

- [ ] **Step 3: Add Spring AOP dependency**

Modify `pom.xml` dependencies section and add:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
```

- [ ] **Step 4: Add `RateLimit` annotation**

Create `src/main/java/com/example/ratelimiter/annotation/RateLimit.java`:

```java
package com.example.ratelimiter.annotation;

import com.example.ratelimiter.config.AlgorithmType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    String key() default "";

    AlgorithmType algorithm() default AlgorithmType.TOKEN_BUCKET;

    long capacity() default 100;

    double ratePerSecond() default 10.0;

    long windowMillis() default 1000;

    int permits() default 1;
}
```

- [ ] **Step 5: Add `RateLimitException`**

Create `src/main/java/com/example/ratelimiter/exception/RateLimitException.java`:

```java
package com.example.ratelimiter.exception;

public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
```

- [ ] **Step 6: Run annotation test**

Run:

```powershell
mvn -f .worktrees\rate-limit-aop\pom.xml test -Dtest=RateLimitAspectTest
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

Run:

```powershell
git -C .worktrees\rate-limit-aop add pom.xml src/main/java/com/example/ratelimiter/annotation/RateLimit.java src/main/java/com/example/ratelimiter/exception/RateLimitException.java src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java
git -C .worktrees\rate-limit-aop commit -m "feat: add rate limit annotation api"
```

## Task 2: AOP Aspect and Integration Tests

**Files:**

- Create: `src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java`
- Modify: `src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java`
- Modify: `src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java`

- [ ] **Step 1: Replace the test with Spring AOP integration tests**

Replace `RateLimitAspectTest.java` with:

```java
package com.example.ratelimiter.aop;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.core.RateLimiterFactory;
import com.example.ratelimiter.exception.RateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = RateLimitAspectTest.TestConfig.class)
class RateLimitAspectTest {

    @Autowired
    private TestService testService;

    @Test
    void annotatedMethodAllowsCallsWithinCapacity() {
        assertThat(testService.limitedTwice()).isEqualTo("limited");
        assertThat(testService.limitedTwice()).isEqualTo("limited");
    }

    @Test
    void annotatedMethodThrowsAfterCapacityIsExhausted() {
        assertThat(testService.rejectAfterOne()).isEqualTo("first");

        assertThatThrownBy(() -> testService.rejectAfterOne())
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Rate limit exceeded for key: aspect:reject-after-one");
    }

    @Test
    void unannotatedMethodIsNotLimited() {
        assertThat(testService.unlimited()).isEqualTo("unlimited");
        assertThat(testService.unlimited()).isEqualTo("unlimited");
        assertThat(testService.unlimited()).isEqualTo("unlimited");
    }

    @Test
    void blankKeyUsesClassAndMethodName() {
        assertThat(testService.defaultKey()).isEqualTo("default");

        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(testService);
        String expectedKey = targetClass.getName() + "#defaultKey";
        assertThatThrownBy(() -> testService.defaultKey())
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining(expectedKey);
    }

    @TestConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        RateLimiterFactory rateLimiterFactory() {
            return new RateLimiterFactory();
        }

        @Bean
        RateLimitAspect rateLimitAspect(RateLimiterFactory rateLimiterFactory) {
            return new RateLimitAspect(rateLimiterFactory);
        }

        @Bean
        TestService testService() {
            return new TestService();
        }
    }

    public static class TestService {

        @RateLimit(key = "aspect:limited-twice", capacity = 2, ratePerSecond = 0.0)
        public String limitedTwice() {
            return "limited";
        }

        @RateLimit(key = "aspect:reject-after-one", capacity = 1, ratePerSecond = 0.0)
        public String rejectAfterOne() {
            return "first";
        }

        public String unlimited() {
            return "unlimited";
        }

        @RateLimit(capacity = 1, ratePerSecond = 0.0)
        public String defaultKey() {
            return "default";
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\rate-limit-aop\pom.xml test -Dtest=RateLimitAspectTest
```

Expected: compilation fails because `RateLimitAspect` does not exist.

- [ ] **Step 3: Make `RateLimiterFactory` a Spring component**

Modify `src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java`:

```java
package com.example.ratelimiter.core;

import com.example.ratelimiter.algorithm.FixedWindowRateLimiter;
import com.example.ratelimiter.algorithm.LeakyBucketRateLimiter;
import com.example.ratelimiter.algorithm.SlidingWindowRateLimiter;
import com.example.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.example.ratelimiter.config.RateLimiterConfig;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
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
            case DISTRIBUTED_TOKEN_BUCKET -> throw new IllegalArgumentException(
                    "distributed token bucket requires RedisRateLimiter with a RedisCommandExecutor"
            );
        };
    }
}
```

- [ ] **Step 4: Add `RateLimitAspect`**

Create `src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java`:

```java
package com.example.ratelimiter.aop;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.core.RateLimiterFactory;
import com.example.ratelimiter.exception.RateLimitException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

@Aspect
@Component
public class RateLimitAspect {

    private final RateLimiterFactory rateLimiterFactory;

    public RateLimitAspect(RateLimiterFactory rateLimiterFactory) {
        this.rateLimiterFactory = Objects.requireNonNull(rateLimiterFactory, "rateLimiterFactory must not be null");
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = resolveKey(joinPoint, rateLimit);
        RateLimiterConfig config = RateLimiterConfig.builder(rateLimit.algorithm())
                .capacity(rateLimit.capacity())
                .ratePerSecond(rateLimit.ratePerSecond())
                .window(Duration.ofMillis(rateLimit.windowMillis()))
                .build();
        RateLimiter limiter = rateLimiterFactory.getOrCreate(key, config);
        if (!limiter.tryAcquire(rateLimit.permits())) {
            throw new RateLimitException("Rate limit exceeded for key: " + key);
        }
        return joinPoint.proceed();
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        if (!rateLimit.key().isBlank()) {
            return rateLimit.key();
        }
        return joinPoint.getSignature().getDeclaringTypeName() + "#" + joinPoint.getSignature().getName();
    }
}
```

- [ ] **Step 5: Run AOP tests**

Run:

```powershell
mvn -f .worktrees\rate-limit-aop\pom.xml test -Dtest=RateLimitAspectTest
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

Run:

```powershell
git -C .worktrees\rate-limit-aop add src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java
git -C .worktrees\rate-limit-aop commit -m "feat: add rate limit aop interceptor"
```

## Task 3: README and Final Verification

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Document annotation usage**

Add this section before `## 文档`:

````markdown
## Spring 注解接入

Phase 5.1 引入最小注解式接入方式。业务方法可以通过 `@RateLimit` 使用单机限流器：

```java
@RateLimit(
        key = "order:create",
        algorithm = AlgorithmType.TOKEN_BUCKET,
        capacity = 100,
        ratePerSecond = 10.0,
        windowMillis = 1000,
        permits = 1
)
public void createOrder() {
    // business logic
}
```

当前注解接入范围：

- 仅支持本地限流算法。
- 被限流时快速失败并抛出 `RateLimitException`。
- 暂不支持 YAML 规则、SPI 扩展、Redis 分布式模式和自适应模式。
````

Also update the current phase section:

```markdown
Phase 5.1: Spring `@RateLimit` 注解接入。
```

Add to completed list:

```markdown
- Spring `@RateLimit` 注解和 AOP 快速失败接入
```

Update next step list to:

```markdown
- YAML/properties 规则提供
- SPI 扩展点
- 补充 Guava/Sentinel 对比入口
```

- [ ] **Step 2: Run all tests**

Run:

```powershell
mvn -f .worktrees\rate-limit-aop\pom.xml test
```

Expected: all tests pass.

- [ ] **Step 3: Build benchmark jar**

Run:

```powershell
mvn -f .worktrees\rate-limit-aop\pom.xml -Pbenchmark -DskipTests package
```

Expected: `BUILD SUCCESS` and `target\benchmarks.jar` exists.

- [ ] **Step 4: Run JMH smoke benchmark**

Run:

```powershell
java -jar .worktrees\rate-limit-aop\target\benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
```

Expected: JMH completes and prints one `LocalRateLimiterBenchmark.tokenBucketSingleThread` result row.

- [ ] **Step 5: Commit docs**

Run:

```powershell
git -C .worktrees\rate-limit-aop add README.md
git -C .worktrees\rate-limit-aop commit -m "docs: document rate limit annotation usage"
```

## Self-Review Checklist

Spec coverage:

- Method-level `@RateLimit` annotation is implemented in Task 1.
- Spring AOP interception is implemented in Task 2.
- Annotation-to-`RateLimiterConfig` conversion is implemented in Task 2.
- `RateLimiterFactory` reuse is implemented in Task 2.
- Fast-fail `RateLimitException` behavior is implemented in Task 2.
- README documents usage and limitations in Task 3.

Verification:

- Tests cover allowed calls, rejected calls, unannotated methods, and blank-key default resolution.
- Full Maven test suite must pass before merge.
- Benchmark jar build and JMH smoke must still work.

Scope boundaries:

- No YAML/properties rule loading in this phase.
- No SPI extension points in this phase.
- No Redis distributed annotation mode in this phase.
- No adaptive annotation mode in this phase.
- No queueing or blocking wait mode in this phase.
