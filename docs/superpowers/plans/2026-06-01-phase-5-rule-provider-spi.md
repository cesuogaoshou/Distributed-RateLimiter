# Phase 5.4 RuleProvider SPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Java SPI rule provider and wire it into `@RateLimit` rule resolution before YAML/properties and annotation fallback.

**Architecture:** Add `RuleProvider`, `EmptyRuleProvider`, and `RuleProviderLoader` to the existing `spi` package, mirroring the `RejectHandler` SPI pattern. Extend `RateLimitAspect` with an injected SPI provider while keeping exactly one Spring `@Autowired` constructor to avoid context-loading ambiguity.

**Tech Stack:** Java 17, Java `ServiceLoader`, Spring AOP, Maven, JUnit 5, AssertJ, Mockito.

---

## File Structure

Create or modify:

```text
README.md
src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java
src/main/java/com/example/ratelimiter/spi/EmptyRuleProvider.java
src/main/java/com/example/ratelimiter/spi/RuleProvider.java
src/main/java/com/example/ratelimiter/spi/RuleProviderLoader.java
src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java
src/test/java/com/example/ratelimiter/spi/EmptyRuleProviderTest.java
src/test/java/com/example/ratelimiter/spi/RuleProviderLoaderTest.java
```

Responsibilities:

- `RuleProvider`: public SPI contract for external rule lookup.
- `EmptyRuleProvider`: fallback provider that never returns a rule.
- `RuleProviderLoader`: selects the highest-priority `RuleProvider` from `ServiceLoader`, or returns `EmptyRuleProvider`.
- `RateLimitAspect`: resolves rules in order: SPI, YAML/properties, annotation.
- `EmptyRuleProviderTest`: verifies fallback provider behavior.
- `RuleProviderLoaderTest`: verifies fallback and priority selection.
- `RateLimitAspectTest`: verifies SPI precedence and existing fallback behavior.
- `README.md`: documents how to implement and register a custom rule provider.

## Task 1: RuleProvider SPI and Empty Provider

**Files:**

- Create: `src/main/java/com/example/ratelimiter/spi/RuleProvider.java`
- Create: `src/main/java/com/example/ratelimiter/spi/EmptyRuleProvider.java`
- Create: `src/test/java/com/example/ratelimiter/spi/EmptyRuleProviderTest.java`

- [ ] **Step 1: Write empty provider test**

Create `src/test/java/com/example/ratelimiter/spi/EmptyRuleProviderTest.java`:

```java
package com.example.ratelimiter.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmptyRuleProviderTest {

    @Test
    void alwaysReturnsEmptyRule() {
        EmptyRuleProvider provider = new EmptyRuleProvider();

        assertThat(provider.findRule("order:create")).isEmpty();
    }

    @Test
    void hasDefaultPriority() {
        assertThat(new EmptyRuleProvider().priority()).isEqualTo(0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\rule-provider-spi\pom.xml test -Dtest=EmptyRuleProviderTest
```

Expected: compilation fails because `EmptyRuleProvider` does not exist.

- [ ] **Step 3: Add SPI interface**

Create `src/main/java/com/example/ratelimiter/spi/RuleProvider.java`:

```java
package com.example.ratelimiter.spi;

import com.example.ratelimiter.rule.RateLimitRule;

import java.util.Optional;

public interface RuleProvider {

    Optional<RateLimitRule> findRule(String key);

    default int priority() {
        return 0;
    }
}
```

- [ ] **Step 4: Add empty provider**

Create `src/main/java/com/example/ratelimiter/spi/EmptyRuleProvider.java`:

```java
package com.example.ratelimiter.spi;

import com.example.ratelimiter.rule.RateLimitRule;

import java.util.Optional;

public class EmptyRuleProvider implements RuleProvider {

    @Override
    public Optional<RateLimitRule> findRule(String key) {
        return Optional.empty();
    }
}
```

- [ ] **Step 5: Run empty provider tests**

Run:

```powershell
mvn -f .worktrees\rule-provider-spi\pom.xml test -Dtest=EmptyRuleProviderTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

Run:

```powershell
git -C .worktrees\rule-provider-spi add src/main/java/com/example/ratelimiter/spi/RuleProvider.java src/main/java/com/example/ratelimiter/spi/EmptyRuleProvider.java src/test/java/com/example/ratelimiter/spi/EmptyRuleProviderTest.java
git -C .worktrees\rule-provider-spi commit -m "feat: add rule provider spi"
```

## Task 2: RuleProvider Loader

**Files:**

- Create: `src/main/java/com/example/ratelimiter/spi/RuleProviderLoader.java`
- Create: `src/test/java/com/example/ratelimiter/spi/RuleProviderLoaderTest.java`

- [ ] **Step 1: Write loader tests**

Create `src/test/java/com/example/ratelimiter/spi/RuleProviderLoaderTest.java`:

```java
package com.example.ratelimiter.spi;

import com.example.ratelimiter.rule.RateLimitRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RuleProviderLoaderTest {

    @Test
    void fallsBackToEmptyProviderWhenNoProvidersExist() {
        RuleProvider provider = new RuleProviderLoader(List.of()).load();

        assertThat(provider).isInstanceOf(EmptyRuleProvider.class);
        assertThat(provider.findRule("missing")).isEmpty();
    }

    @Test
    void choosesHighestPriorityProvider() {
        RuleProvider low = new StaticRuleProvider(5);
        RuleProvider high = new StaticRuleProvider(10);

        RuleProvider provider = new RuleProviderLoader(List.of(low, high)).load();

        assertThat(provider).isSameAs(high);
    }

    private static class StaticRuleProvider implements RuleProvider {

        private final int priority;

        private StaticRuleProvider(int priority) {
            this.priority = priority;
        }

        @Override
        public Optional<RateLimitRule> findRule(String key) {
            return Optional.empty();
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
mvn -f .worktrees\rule-provider-spi\pom.xml test -Dtest=RuleProviderLoaderTest
```

Expected: compilation fails because `RuleProviderLoader` does not exist.

- [ ] **Step 3: Add loader**

Create `src/main/java/com/example/ratelimiter/spi/RuleProviderLoader.java`:

```java
package com.example.ratelimiter.spi;

import java.util.Comparator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

public class RuleProviderLoader {

    private final Iterable<RuleProvider> providers;

    public RuleProviderLoader() {
        this(ServiceLoader.load(RuleProvider.class));
    }

    public RuleProviderLoader(Iterable<RuleProvider> providers) {
        this.providers = Objects.requireNonNull(providers, "providers must not be null");
    }

    public RuleProvider load() {
        return StreamSupport.stream(providers.spliterator(), false)
                .max(Comparator.comparingInt(RuleProvider::priority))
                .orElseGet(EmptyRuleProvider::new);
    }
}
```

- [ ] **Step 4: Run loader tests**

Run:

```powershell
mvn -f .worktrees\rule-provider-spi\pom.xml test -Dtest=RuleProviderLoaderTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

Run:

```powershell
git -C .worktrees\rule-provider-spi add src/main/java/com/example/ratelimiter/spi/RuleProviderLoader.java src/test/java/com/example/ratelimiter/spi/RuleProviderLoaderTest.java
git -C .worktrees\rule-provider-spi commit -m "feat: add rule provider loader"
```

## Task 3: Aspect Rule Resolution Chain

**Files:**

- Modify: `src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java`
- Modify: `src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java`

- [ ] **Step 1: Add SPI precedence tests**

Add imports to `RateLimitAspectTest`:

```java
import com.example.ratelimiter.spi.DefaultRejectHandler;
import com.example.ratelimiter.spi.RuleProvider;

import java.util.Optional;
```

Add this test to `RateLimitAspectTest`:

```java
    @Test
    void spiRuleOverridesConfiguredRuleForSameKey() throws Throwable {
        RateLimitRule spiRule = onePermitRule();
        spiRule.setCapacity(2);
        RuleProvider spiProvider = key -> "aspect:configured-override".equals(key)
                ? Optional.of(spiRule)
                : Optional.empty();
        RateLimitAspect aspect = new RateLimitAspect(
                new RateLimiterFactory(),
                new RateLimitRuleProvider(testPropertiesWithConfiguredOverride()),
                new DefaultRejectHandler(),
                spiProvider
        );
        ProceedingJoinPoint joinPoint = mockJoinPointReturning("configured");
        RateLimit rateLimit = TestService.class.getMethod("configuredOverride").getAnnotation(RateLimit.class);

        assertThat(aspect.around(joinPoint, rateLimit)).isEqualTo("configured");
        assertThat(aspect.around(joinPoint, rateLimit)).isEqualTo("configured");

        verify(joinPoint, times(2)).proceed();
    }
```

Add this test to `RateLimitAspectTest`:

```java
    @Test
    void configuredRuleStillAppliesWhenSpiProviderMisses() throws Throwable {
        RuleProvider spiProvider = key -> Optional.empty();
        RateLimitAspect aspect = new RateLimitAspect(
                new RateLimiterFactory(),
                new RateLimitRuleProvider(testPropertiesWithConfiguredOverride()),
                new DefaultRejectHandler(),
                spiProvider
        );
        ProceedingJoinPoint joinPoint = mockJoinPointReturning("configured");
        RateLimit rateLimit = TestService.class.getMethod("configuredOverride").getAnnotation(RateLimit.class);

        assertThat(aspect.around(joinPoint, rateLimit)).isEqualTo("configured");
        assertThatThrownBy(() -> aspect.around(joinPoint, rateLimit))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Rate limit exceeded for key: aspect:configured-override");
        verify(joinPoint, times(1)).proceed();
    }
```

Add these helpers inside `RateLimitAspectTest`:

```java
    private static RateLimitProperties testPropertiesWithConfiguredOverride() {
        RateLimitProperties properties = new RateLimitProperties();
        Map<String, RateLimitRule> rules = new HashMap<>();
        rules.put("aspect:configured-override", onePermitRule());
        properties.setRules(rules);
        return properties;
    }

    private static ProceedingJoinPoint mockJoinPointReturning(String result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn(TestService.class.getName());
        when(signature.getName()).thenReturn("configuredOverride");
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\rule-provider-spi\pom.xml test -Dtest=RateLimitAspectTest
```

Expected: compilation fails because `RateLimitAspect` has no constructor accepting `RuleProvider`, or the SPI rule is not used yet.

- [ ] **Step 3: Update aspect**

Modify `src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java`:

```java
import com.example.ratelimiter.spi.RuleProvider;
import com.example.ratelimiter.spi.RuleProviderLoader;
```

Add field:

```java
    private final RuleProvider spiRuleProvider;
```

Keep the one-argument constructor:

```java
    public RateLimitAspect(RateLimiterFactory rateLimiterFactory) {
        this(rateLimiterFactory, new RateLimitRuleProvider(new RateLimitProperties()));
    }
```

Keep exactly one Spring constructor:

```java
    @Autowired
    public RateLimitAspect(RateLimiterFactory rateLimiterFactory, RateLimitRuleProvider ruleProvider) {
        this(
                rateLimiterFactory,
                ruleProvider,
                new RejectHandlerLoader().load(),
                new RuleProviderLoader().load()
        );
    }
```

Replace the current three-argument constructor with:

```java
    public RateLimitAspect(
            RateLimiterFactory rateLimiterFactory,
            RateLimitRuleProvider ruleProvider,
            RejectHandler rejectHandler) {
        this(rateLimiterFactory, ruleProvider, rejectHandler, new RuleProviderLoader().load());
    }
```

Add four-argument constructor:

```java
    public RateLimitAspect(
            RateLimiterFactory rateLimiterFactory,
            RateLimitRuleProvider ruleProvider,
            RejectHandler rejectHandler,
            RuleProvider spiRuleProvider) {
        this.rateLimiterFactory = Objects.requireNonNull(rateLimiterFactory, "rateLimiterFactory must not be null");
        this.ruleProvider = Objects.requireNonNull(ruleProvider, "ruleProvider must not be null");
        this.rejectHandler = Objects.requireNonNull(rejectHandler, "rejectHandler must not be null");
        this.spiRuleProvider = Objects.requireNonNull(spiRuleProvider, "spiRuleProvider must not be null");
    }
```

Update `resolveRule`:

```java
    private ResolvedRule resolveRule(String key, RateLimit rateLimit) {
        return spiRuleProvider.findRule(key)
                .or(() -> ruleProvider.findRule(key))
                .map(rule -> new ResolvedRule(rule.toConfig(), rule.getPermits()))
                .orElseGet(() -> new ResolvedRule(toConfig(rateLimit), rateLimit.permits()));
    }
```

Do not annotate the four-argument constructor with `@Autowired`.

- [ ] **Step 4: Run aspect tests**

Run:

```powershell
mvn -f .worktrees\rule-provider-spi\pom.xml test -Dtest=RateLimitAspectTest
```

Expected: AOP tests pass and include SPI precedence coverage.

- [ ] **Step 5: Commit**

Run:

```powershell
git -C .worktrees\rule-provider-spi add src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java
git -C .worktrees\rule-provider-spi commit -m "feat: resolve rate limit rules from spi"
```

## Task 4: README and Final Verification

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Document RuleProvider SPI**

Update current phase:

```markdown
Phase 5.4: RuleProvider SPI 扩展点。
```

Add completed item:

```markdown
- Java SPI `RuleProvider` 规则提供扩展点
```

Update next step list:

```markdown
- RateLimiterAlgorithm SPI 扩展点
- 补充 Guava/Sentinel 对比入口
```

Extend the existing `## SPI 扩展点` section with:

````markdown
### RuleProvider

`RuleProvider` 可用于从外部 jar 提供限流规则。解析优先级是：

1. SPI `RuleProvider`
2. YAML/properties `RateLimitRuleProvider`
3. `@RateLimit` 注解参数

自定义规则提供者：

```java
public class CustomRuleProvider implements RuleProvider {

    @Override
    public Optional<RateLimitRule> findRule(String key) {
        if (!"order:create".equals(key)) {
            return Optional.empty();
        }
        RateLimitRule rule = new RateLimitRule();
        rule.setAlgorithm(AlgorithmType.TOKEN_BUCKET);
        rule.setCapacity(100);
        rule.setRatePerSecond(10.0);
        rule.setWindowMillis(1000);
        rule.setPermits(1);
        return Optional.of(rule);
    }

    @Override
    public int priority() {
        return 100;
    }
}
```

注册文件：

```text
META-INF/services/com.example.ratelimiter.spi.RuleProvider
```

文件内容：

```text
com.example.demo.CustomRuleProvider
```

当前 `RuleProvider` SPI 只选择 `priority()` 最大的一个 provider，不合并多个 provider，也不支持动态刷新。
````

Update the SPI scope list:

```markdown
- 已支持 `RejectHandler`。
- 已支持 `RuleProvider`。
- 多个同类 SPI 实现同时存在时，选择 `priority()` 最大的实现。
- 暂不支持 `RateLimiterAlgorithm` SPI。
```

- [ ] **Step 2: Run all tests**

Run:

```powershell
mvn -f .worktrees\rule-provider-spi\pom.xml test
```

Expected: all tests pass.

- [ ] **Step 3: Build benchmark jar**

Run:

```powershell
mvn -f .worktrees\rule-provider-spi\pom.xml -Pbenchmark -DskipTests package
```

Expected: `BUILD SUCCESS` and `target\benchmarks.jar` exists.

- [ ] **Step 4: Run JMH smoke benchmark**

Run:

```powershell
java -jar .worktrees\rule-provider-spi\target\benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
```

Expected: JMH completes and prints one `LocalRateLimiterBenchmark.tokenBucketSingleThread` result row.

- [ ] **Step 5: Commit docs**

Run:

```powershell
git -C .worktrees\rule-provider-spi add README.md
git -C .worktrees\rule-provider-spi commit -m "docs: document rule provider spi"
```

## Self-Review Checklist

Spec coverage:

- `RuleProvider` SPI is covered by Task 1.
- Empty fallback provider is covered by Task 1.
- ServiceLoader-based loader is covered by Task 2.
- Highest-priority provider selection is covered by Task 2.
- SPI before YAML/properties before annotation is covered by Task 3.
- Spring constructor ambiguity is addressed in Task 3.
- README SPI docs are covered by Task 4.

Verification:

- Empty provider tests cover fallback behavior.
- Loader tests cover provider selection.
- Aspect tests cover SPI override and fallback order.
- Full Maven tests, benchmark packaging, and JMH smoke are required before merge.

Scope boundaries:

- No provider chain execution.
- No merging multiple providers.
- No dynamic reload.
- No remote config provider.
- No `RateLimiterAlgorithm` SPI in this phase.
