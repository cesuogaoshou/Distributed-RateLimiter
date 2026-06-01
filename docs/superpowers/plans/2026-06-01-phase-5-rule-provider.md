# Phase 5.2 Rule Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Spring configuration-file rule support so `@RateLimit(key = "...")` can resolve limiter settings from `ratelimiter.rules` before falling back to annotation attributes.

**Architecture:** Add a small `rule` package with a typed rule model, Spring Boot configuration properties, and a lookup provider. Update `RateLimitAspect` to resolve the limiter key first, query the provider, and use the configured rule when present while preserving the existing constructor and annotation-only behavior.

**Tech Stack:** Java 17, Spring Boot 3 configuration properties, Spring AOP, Maven, JUnit 5, AssertJ.

---

## File Structure

Create or modify:

```text
README.md
src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java
src/main/java/com/example/ratelimiter/rule/RateLimitProperties.java
src/main/java/com/example/ratelimiter/rule/RateLimitRule.java
src/main/java/com/example/ratelimiter/rule/RateLimitRuleProvider.java
src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java
src/test/java/com/example/ratelimiter/rule/RateLimitRuleProviderTest.java
src/test/java/com/example/ratelimiter/rule/RateLimitRuleTest.java
```

Responsibilities:

- `RateLimitRule`: one configured limiter rule and conversion to `RateLimiterConfig`.
- `RateLimitProperties`: Spring-bound `ratelimiter.rules` map.
- `RateLimitRuleProvider`: lookup provider for configured rules.
- `RateLimitAspect`: config-rule-first resolution, annotation fallback.
- `RateLimitRuleTest`: rule defaults and conversion behavior.
- `RateLimitRuleProviderTest`: provider lookup behavior.
- `RateLimitAspectTest`: existing annotation behavior plus configured-rule precedence and blank-key configured lookup.
- `README.md`: document YAML/properties usage and precedence.

## Task 1: Rule Model

**Files:**

- Create: `src/main/java/com/example/ratelimiter/rule/RateLimitRule.java`
- Create: `src/test/java/com/example/ratelimiter/rule/RateLimitRuleTest.java`

- [ ] **Step 1: Write rule model tests**

Create `src/test/java/com/example/ratelimiter/rule/RateLimitRuleTest.java`:

```java
package com.example.ratelimiter.rule;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitRuleTest {

    @Test
    void convertsRuleToRateLimiterConfig() {
        RateLimitRule rule = new RateLimitRule();
        rule.setAlgorithm(AlgorithmType.FIXED_WINDOW);
        rule.setCapacity(5);
        rule.setRatePerSecond(0.0);
        rule.setWindowMillis(2500);
        rule.setPermits(2);

        RateLimiterConfig config = rule.toConfig();

        assertThat(config.algorithm()).isEqualTo(AlgorithmType.FIXED_WINDOW);
        assertThat(config.capacity()).isEqualTo(5);
        assertThat(config.ratePerSecond()).isEqualTo(0.0);
        assertThat(config.window()).isEqualTo(Duration.ofMillis(2500));
        assertThat(rule.getPermits()).isEqualTo(2);
    }

    @Test
    void defaultsMatchRateLimitAnnotationDefaults() {
        RateLimitRule rule = new RateLimitRule();

        RateLimiterConfig config = rule.toConfig();

        assertThat(config.algorithm()).isEqualTo(AlgorithmType.TOKEN_BUCKET);
        assertThat(config.capacity()).isEqualTo(100);
        assertThat(config.ratePerSecond()).isEqualTo(10.0);
        assertThat(config.window()).isEqualTo(Duration.ofMillis(1000));
        assertThat(rule.getPermits()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\rule-provider\pom.xml test -Dtest=RateLimitRuleTest
```

Expected: compilation fails because `RateLimitRule` does not exist.

- [ ] **Step 3: Add rule model**

Create `src/main/java/com/example/ratelimiter/rule/RateLimitRule.java`:

```java
package com.example.ratelimiter.rule;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;

import java.time.Duration;

public class RateLimitRule {

    private AlgorithmType algorithm = AlgorithmType.TOKEN_BUCKET;
    private long capacity = 100;
    private double ratePerSecond = 10.0;
    private long windowMillis = 1000;
    private int permits = 1;

    public RateLimiterConfig toConfig() {
        return RateLimiterConfig.builder(algorithm)
                .capacity(capacity)
                .ratePerSecond(ratePerSecond)
                .window(Duration.ofMillis(windowMillis))
                .build();
    }

    public AlgorithmType getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(AlgorithmType algorithm) {
        this.algorithm = algorithm;
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public double getRatePerSecond() {
        return ratePerSecond;
    }

    public void setRatePerSecond(double ratePerSecond) {
        this.ratePerSecond = ratePerSecond;
    }

    public long getWindowMillis() {
        return windowMillis;
    }

    public void setWindowMillis(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public int getPermits() {
        return permits;
    }

    public void setPermits(int permits) {
        this.permits = permits;
    }
}
```

- [ ] **Step 4: Run rule tests**

Run:

```powershell
mvn -f .worktrees\rule-provider\pom.xml test -Dtest=RateLimitRuleTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

Run:

```powershell
git -C .worktrees\rule-provider add src/main/java/com/example/ratelimiter/rule/RateLimitRule.java src/test/java/com/example/ratelimiter/rule/RateLimitRuleTest.java
git -C .worktrees\rule-provider commit -m "feat: add rate limit rule model"
```

## Task 2: Configuration Properties and Provider

**Files:**

- Create: `src/main/java/com/example/ratelimiter/rule/RateLimitProperties.java`
- Create: `src/main/java/com/example/ratelimiter/rule/RateLimitRuleProvider.java`
- Create: `src/test/java/com/example/ratelimiter/rule/RateLimitRuleProviderTest.java`

- [ ] **Step 1: Write provider tests**

Create `src/test/java/com/example/ratelimiter/rule/RateLimitRuleProviderTest.java`:

```java
package com.example.ratelimiter.rule;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitRuleProviderTest {

    @Test
    void findsConfiguredRuleByKey() {
        RateLimitRule rule = new RateLimitRule();
        rule.setCapacity(1);
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRules(Map.of("order:create", rule));
        RateLimitRuleProvider provider = new RateLimitRuleProvider(properties);

        assertThat(provider.findRule("order:create")).containsSame(rule);
    }

    @Test
    void returnsEmptyForMissingKey() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRules(Map.of("order:create", new RateLimitRule()));
        RateLimitRuleProvider provider = new RateLimitRuleProvider(properties);

        assertThat(provider.findRule("order:cancel")).isEmpty();
    }

    @Test
    void handlesNullRulesAsEmpty() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRules(null);
        RateLimitRuleProvider provider = new RateLimitRuleProvider(properties);

        assertThat(provider.findRule("anything")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\rule-provider\pom.xml test -Dtest=RateLimitRuleProviderTest
```

Expected: compilation fails because `RateLimitProperties` and `RateLimitRuleProvider` do not exist.

- [ ] **Step 3: Add properties class**

Create `src/main/java/com/example/ratelimiter/rule/RateLimitProperties.java`:

```java
package com.example.ratelimiter.rule;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "ratelimiter")
public class RateLimitProperties {

    private Map<String, RateLimitRule> rules = new HashMap<>();

    public Map<String, RateLimitRule> getRules() {
        return rules;
    }

    public void setRules(Map<String, RateLimitRule> rules) {
        this.rules = rules == null ? new HashMap<>() : new HashMap<>(rules);
    }
}
```

- [ ] **Step 4: Add provider**

Create `src/main/java/com/example/ratelimiter/rule/RateLimitRuleProvider.java`:

```java
package com.example.ratelimiter.rule;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitRuleProvider {

    private final RateLimitProperties properties;

    public RateLimitRuleProvider(RateLimitProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public Optional<RateLimitRule> findRule(String key) {
        return Optional.ofNullable(properties.getRules().get(key));
    }
}
```

- [ ] **Step 5: Run provider tests**

Run:

```powershell
mvn -f .worktrees\rule-provider\pom.xml test -Dtest=RateLimitRuleProviderTest
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

Run:

```powershell
git -C .worktrees\rule-provider add src/main/java/com/example/ratelimiter/rule/RateLimitProperties.java src/main/java/com/example/ratelimiter/rule/RateLimitRuleProvider.java src/test/java/com/example/ratelimiter/rule/RateLimitRuleProviderTest.java
git -C .worktrees\rule-provider commit -m "feat: add rate limit rule provider"
```

## Task 3: Aspect Rule Resolution

**Files:**

- Modify: `src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java`
- Modify: `src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java`

- [ ] **Step 1: Replace aspect integration test**

Replace `src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java` with:

```java
package com.example.ratelimiter.aop;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.core.RateLimiterFactory;
import com.example.ratelimiter.exception.RateLimitException;
import com.example.ratelimiter.rule.RateLimitProperties;
import com.example.ratelimiter.rule.RateLimitRule;
import com.example.ratelimiter.rule.RateLimitRuleProvider;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.HashMap;
import java.util.Map;

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
    void configuredRuleOverridesAnnotationAttributes() {
        assertThat(testService.configuredOverride()).isEqualTo("configured");

        assertThatThrownBy(() -> testService.configuredOverride())
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Rate limit exceeded for key: aspect:configured-override");
    }

    @Test
    void unconfiguredKeyFallsBackToAnnotationAttributes() {
        assertThat(testService.annotationFallback()).isEqualTo("fallback");
        assertThat(testService.annotationFallback()).isEqualTo("fallback");
    }

    @Test
    void unannotatedMethodIsNotLimited() {
        assertThat(testService.unlimited()).isEqualTo("unlimited");
        assertThat(testService.unlimited()).isEqualTo("unlimited");
        assertThat(testService.unlimited()).isEqualTo("unlimited");
    }

    @Test
    void blankKeyUsesDefaultKeyForConfiguredRuleLookup() {
        assertThat(testService.defaultKeyConfigured()).isEqualTo("default");

        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(testService);
        String expectedKey = targetClass.getName() + "#defaultKeyConfigured";
        assertThatThrownBy(() -> testService.defaultKeyConfigured())
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
        RateLimitProperties rateLimitProperties() {
            RateLimitProperties properties = new RateLimitProperties();
            Map<String, RateLimitRule> rules = new HashMap<>();
            rules.put("aspect:configured-override", onePermitRule());
            rules.put(TestService.class.getName() + "#defaultKeyConfigured", onePermitRule());
            properties.setRules(rules);
            return properties;
        }

        @Bean
        RateLimitRuleProvider rateLimitRuleProvider(RateLimitProperties properties) {
            return new RateLimitRuleProvider(properties);
        }

        @Bean
        RateLimitAspect rateLimitAspect(
                RateLimiterFactory rateLimiterFactory,
                RateLimitRuleProvider rateLimitRuleProvider) {
            return new RateLimitAspect(rateLimiterFactory, rateLimitRuleProvider);
        }

        @Bean
        TestService testService() {
            return new TestService();
        }

        private static RateLimitRule onePermitRule() {
            RateLimitRule rule = new RateLimitRule();
            rule.setCapacity(1);
            rule.setRatePerSecond(0.0);
            return rule;
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

        @RateLimit(key = "aspect:configured-override", capacity = 10, ratePerSecond = 0.0)
        public String configuredOverride() {
            return "configured";
        }

        @RateLimit(key = "aspect:annotation-fallback", capacity = 2, ratePerSecond = 0.0)
        public String annotationFallback() {
            return "fallback";
        }

        public String unlimited() {
            return "unlimited";
        }

        @RateLimit(capacity = 10, ratePerSecond = 0.0)
        public String defaultKeyConfigured() {
            return "default";
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\rule-provider\pom.xml test -Dtest=RateLimitAspectTest
```

Expected: compilation fails because `RateLimitAspect` has no constructor accepting `RateLimitRuleProvider`, or behavior fails because configured rules are not used yet.

- [ ] **Step 3: Update aspect implementation**

Replace `src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java` with:

```java
package com.example.ratelimiter.aop;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.core.RateLimiterFactory;
import com.example.ratelimiter.exception.RateLimitException;
import com.example.ratelimiter.rule.RateLimitProperties;
import com.example.ratelimiter.rule.RateLimitRule;
import com.example.ratelimiter.rule.RateLimitRuleProvider;
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
    private final RateLimitRuleProvider ruleProvider;

    public RateLimitAspect(RateLimiterFactory rateLimiterFactory) {
        this(rateLimiterFactory, new RateLimitRuleProvider(new RateLimitProperties()));
    }

    public RateLimitAspect(RateLimiterFactory rateLimiterFactory, RateLimitRuleProvider ruleProvider) {
        this.rateLimiterFactory = Objects.requireNonNull(rateLimiterFactory, "rateLimiterFactory must not be null");
        this.ruleProvider = Objects.requireNonNull(ruleProvider, "ruleProvider must not be null");
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = resolveKey(joinPoint, rateLimit);
        ResolvedRule resolvedRule = resolveRule(key, rateLimit);
        RateLimiter limiter = rateLimiterFactory.getOrCreate(key, resolvedRule.config());
        if (!limiter.tryAcquire(resolvedRule.permits())) {
            throw new RateLimitException("Rate limit exceeded for key: " + key);
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

- [ ] **Step 4: Run aspect tests**

Run:

```powershell
mvn -f .worktrees\rule-provider\pom.xml test -Dtest=RateLimitAspectTest
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

Run:

```powershell
git -C .worktrees\rule-provider add src/main/java/com/example/ratelimiter/aop/RateLimitAspect.java src/test/java/com/example/ratelimiter/aop/RateLimitAspectTest.java
git -C .worktrees\rule-provider commit -m "feat: resolve rate limit rules from configuration"
```

## Task 4: README and Final Verification

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Document configured rules**

Update the `## Spring 注解接入` section to include:

````markdown
也可以把规则放到 `application.yml` 或 `application.properties`，注解只保留稳定 key：

```yaml
ratelimiter:
  rules:
    order:create:
      algorithm: TOKEN_BUCKET
      capacity: 100
      rate-per-second: 10.0
      window-millis: 1000
      permits: 1
```

```properties
ratelimiter.rules[order:create].algorithm=TOKEN_BUCKET
ratelimiter.rules[order:create].capacity=100
ratelimiter.rules[order:create].rate-per-second=10.0
ratelimiter.rules[order:create].window-millis=1000
ratelimiter.rules[order:create].permits=1
```

```java
@RateLimit(key = "order:create")
public void createOrder() {
    // business logic
}
```

规则解析优先级：

1. 如果配置文件中存在同名 key，使用配置文件规则。
2. 如果不存在同名 key，使用注解参数。
3. 如果注解 key 为空，使用 `类名#方法名` 作为 key，并同样先查配置文件。
````

Update current phase:

```markdown
Phase 5.2: YAML/properties 规则提供。
```

Add completed item:

```markdown
- YAML/properties 限流规则绑定和配置优先解析
```

Update current limitations in the Spring annotation section:

```markdown
- 暂不支持 Java SPI、动态刷新、Redis 分布式模式和自适应模式。
```

- [ ] **Step 2: Run all tests**

Run:

```powershell
mvn -f .worktrees\rule-provider\pom.xml test
```

Expected: all tests pass.

- [ ] **Step 3: Build benchmark jar**

Run:

```powershell
mvn -f .worktrees\rule-provider\pom.xml -Pbenchmark -DskipTests package
```

Expected: `BUILD SUCCESS` and `target\benchmarks.jar` exists.

- [ ] **Step 4: Run JMH smoke benchmark**

Run:

```powershell
java -jar .worktrees\rule-provider\target\benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
```

Expected: JMH completes and prints one `LocalRateLimiterBenchmark.tokenBucketSingleThread` result row.

- [ ] **Step 5: Commit docs**

Run:

```powershell
git -C .worktrees\rule-provider add README.md
git -C .worktrees\rule-provider commit -m "docs: document configured rate limit rules"
```

## Self-Review Checklist

Spec coverage:

- `ratelimiter.rules` binding is covered by Task 2.
- Typed rule model is covered by Task 1.
- Rule lookup provider is covered by Task 2.
- Config-before-annotation aspect behavior is covered by Task 3.
- Annotation fallback is covered by Task 3.
- Blank-key default lookup is covered by Task 3.
- README documents configuration and precedence in Task 4.

Verification:

- Rule model tests cover conversion and defaults.
- Provider tests cover hit, miss, and null map behavior.
- Aspect tests cover old behavior plus configured-rule override.
- Full Maven tests, benchmark packaging, and JMH smoke are required before merge.

Scope boundaries:

- No Java SPI in this phase.
- No dynamic refresh in this phase.
- No Redis distributed annotation mode in this phase.
- No adaptive annotation mode in this phase.
- No custom reject handlers in this phase.
