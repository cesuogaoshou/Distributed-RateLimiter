# Phase 3 Distributed Redis Rate Limiting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Redis-backed distributed token bucket rate limiting with Lua atomicity, a Java wrapper that implements the existing `RateLimiter` interface, health checks, and fallback-to-local behavior.

**Architecture:** Keep distributed behavior in a separate `distributed` package and keep the existing local algorithms unchanged. Redis access is abstracted behind a small `RedisCommandExecutor` interface so Lua behavior and fallback logic can be unit-tested without requiring Docker/Testcontainers in every test. A real Spring Data Redis adapter is added for runtime integration, while tests use an in-memory fake executor.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data Redis, Redis Lua, JUnit 5, existing local limiter core.

---

## File Structure

Create or modify:

```text
pom.xml
README.md
src/main/java/com/example/ratelimiter/config/AlgorithmType.java
src/main/java/com/example/ratelimiter/distributed/DegradingRateLimiter.java
src/main/java/com/example/ratelimiter/distributed/RedisCommandException.java
src/main/java/com/example/ratelimiter/distributed/RedisCommandExecutor.java
src/main/java/com/example/ratelimiter/distributed/RedisHealthChecker.java
src/main/java/com/example/ratelimiter/distributed/RedisRateLimiter.java
src/main/java/com/example/ratelimiter/distributed/SpringDataRedisCommandExecutor.java
src/main/resources/ratelimiter/lua/token_bucket.lua
src/test/java/com/example/ratelimiter/distributed/DegradingRateLimiterTest.java
src/test/java/com/example/ratelimiter/distributed/FakeRedisCommandExecutor.java
src/test/java/com/example/ratelimiter/distributed/RedisRateLimiterTest.java
```

Responsibilities:

- `RedisCommandExecutor`: small boundary for Redis `EVAL` and health `PING`.
- `SpringDataRedisCommandExecutor`: production adapter around `StringRedisTemplate`.
- `RedisRateLimiter`: implements `RateLimiter` and delegates atomic state transitions to Lua.
- `DegradingRateLimiter`: tries distributed limiter first, falls back to local limiter when Redis is unhealthy or throws.
- `RedisHealthChecker`: tracks Redis availability through the executor.
- `FakeRedisCommandExecutor`: deterministic in-memory test double for unit tests.
- `token_bucket.lua`: canonical Redis token bucket script.

## Task 1: Add Redis Dependency and Distributed Algorithm Type

**Files:**

- Modify: `pom.xml`
- Modify: `src/main/java/com/example/ratelimiter/config/AlgorithmType.java`

- [ ] **Step 1: Add Spring Data Redis dependency**

Add this dependency to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

- [ ] **Step 2: Add distributed token bucket enum**

Modify `AlgorithmType.java`:

```java
public enum AlgorithmType {
    TOKEN_BUCKET,
    LEAKY_BUCKET,
    FIXED_WINDOW,
    SLIDING_WINDOW,
    DISTRIBUTED_TOKEN_BUCKET
}
```

- [ ] **Step 3: Run tests**

Run:

```powershell
mvn test
```

Expected: existing 27 tests pass.

- [ ] **Step 4: Commit**

Run:

```powershell
git add pom.xml src/main/java/com/example/ratelimiter/config/AlgorithmType.java
git commit -m "build: add redis support dependency"
```

## Task 2: Add Redis Command Boundary and Lua Script

**Files:**

- Create: `src/main/java/com/example/ratelimiter/distributed/RedisCommandException.java`
- Create: `src/main/java/com/example/ratelimiter/distributed/RedisCommandExecutor.java`
- Create: `src/main/java/com/example/ratelimiter/distributed/SpringDataRedisCommandExecutor.java`
- Create: `src/main/resources/ratelimiter/lua/token_bucket.lua`

- [ ] **Step 1: Add Redis exception**

Add `RedisCommandException.java`:

```java
package com.example.ratelimiter.distributed;

public class RedisCommandException extends RuntimeException {

    public RedisCommandException(String message) {
        super(message);
    }

    public RedisCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 2: Add Redis executor interface**

Add `RedisCommandExecutor.java`:

```java
package com.example.ratelimiter.distributed;

import java.util.List;

public interface RedisCommandExecutor {

    List<Long> evalTokenBucket(String key, long capacity, double refillRatePerSecond, long permits, long nowMillis);

    boolean ping();
}
```

The Lua result contract is:

```text
[allowedFlag, remainingTokens]
```

Where `allowedFlag` is `1` for allowed and `0` for rejected.

- [ ] **Step 3: Add Spring Data Redis adapter**

Add `SpringDataRedisCommandExecutor.java`:

```java
package com.example.ratelimiter.distributed;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

public class SpringDataRedisCommandExecutor implements RedisCommandExecutor {

    private static final String TOKEN_BUCKET_SCRIPT = """
            local bucket_key = KEYS[1]
            local tokens_key = bucket_key .. ':tokens'
            local timestamp_key = bucket_key .. ':timestamp'

            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local permits = tonumber(ARGV[3])
            local now = tonumber(ARGV[4])

            local current_tokens = tonumber(redis.call('GET', tokens_key))
            if current_tokens == nil then
                current_tokens = capacity
            end

            local last_refill = tonumber(redis.call('GET', timestamp_key))
            if last_refill == nil then
                last_refill = now
            end

            local elapsed = math.max(0, now - last_refill) / 1000
            local refill = elapsed * refill_rate
            current_tokens = math.min(capacity, current_tokens + refill)

            local allowed = 0
            if current_tokens >= permits then
                current_tokens = current_tokens - permits
                allowed = 1
            end

            redis.call('SET', tokens_key, current_tokens)
            redis.call('SET', timestamp_key, now)
            redis.call('PEXPIRE', tokens_key, 60000)
            redis.call('PEXPIRE', timestamp_key, 60000)

            return { allowed, math.floor(current_tokens) }
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;

    public SpringDataRedisCommandExecutor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, List.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Long> evalTokenBucket(String key, long capacity, double refillRatePerSecond, long permits, long nowMillis) {
        try {
            return (List<Long>) redisTemplate.execute(
                    tokenBucketScript,
                    List.of(key),
                    Long.toString(capacity),
                    Double.toString(refillRatePerSecond),
                    Long.toString(permits),
                    Long.toString(nowMillis)
            );
        } catch (RuntimeException ex) {
            throw new RedisCommandException("failed to execute redis token bucket script", ex);
        }
    }

    @Override
    public boolean ping() {
        try {
            String result = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(result);
        } catch (RedisConnectionFailureException ex) {
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Add standalone Lua script resource**

Add `src/main/resources/ratelimiter/lua/token_bucket.lua`:

```lua
local bucket_key = KEYS[1]
local tokens_key = bucket_key .. ':tokens'
local timestamp_key = bucket_key .. ':timestamp'

local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local permits = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

local current_tokens = tonumber(redis.call('GET', tokens_key))
if current_tokens == nil then
    current_tokens = capacity
end

local last_refill = tonumber(redis.call('GET', timestamp_key))
if last_refill == nil then
    last_refill = now
end

local elapsed = math.max(0, now - last_refill) / 1000
local refill = elapsed * refill_rate
current_tokens = math.min(capacity, current_tokens + refill)

local allowed = 0
if current_tokens >= permits then
    current_tokens = current_tokens - permits
    allowed = 1
end

redis.call('SET', tokens_key, current_tokens)
redis.call('SET', timestamp_key, now)
redis.call('PEXPIRE', tokens_key, 60000)
redis.call('PEXPIRE', timestamp_key, 60000)

return { allowed, math.floor(current_tokens) }
```

- [ ] **Step 5: Run tests**

Run:

```powershell
mvn test
```

Expected: existing tests pass.

- [ ] **Step 6: Commit**

Run:

```powershell
git add src/main/java/com/example/ratelimiter/distributed src/main/resources/ratelimiter/lua/token_bucket.lua
git commit -m "feat: add redis command boundary and lua script"
```

## Task 3: Implement RedisRateLimiter with Unit Tests

**Files:**

- Create: `src/main/java/com/example/ratelimiter/distributed/RedisRateLimiter.java`
- Create: `src/test/java/com/example/ratelimiter/distributed/FakeRedisCommandExecutor.java`
- Create: `src/test/java/com/example/ratelimiter/distributed/RedisRateLimiterTest.java`

- [ ] **Step 1: Add fake Redis executor**

Add `FakeRedisCommandExecutor.java`:

```java
package com.example.ratelimiter.distributed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakeRedisCommandExecutor implements RedisCommandExecutor {

    private final Map<String, BucketState> buckets = new HashMap<>();
    private boolean healthy = true;
    private boolean failCommands;

    @Override
    public synchronized List<Long> evalTokenBucket(String key, long capacity, double refillRatePerSecond, long permits, long nowMillis) {
        if (failCommands) {
            throw new RedisCommandException("simulated redis failure");
        }
        BucketState state = buckets.computeIfAbsent(key, ignored -> new BucketState(capacity, nowMillis));
        long elapsedMillis = Math.max(0, nowMillis - state.lastRefillMillis);
        double refill = elapsedMillis / 1000.0 * refillRatePerSecond;
        state.tokens = Math.min(capacity, state.tokens + refill);
        state.lastRefillMillis = nowMillis;

        long allowed = 0;
        if (state.tokens >= permits) {
            state.tokens -= permits;
            allowed = 1;
        }
        return List.of(allowed, (long) state.tokens);
    }

    @Override
    public boolean ping() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public void setFailCommands(boolean failCommands) {
        this.failCommands = failCommands;
    }

    private static final class BucketState {
        private double tokens;
        private long lastRefillMillis;

        private BucketState(double tokens, long lastRefillMillis) {
            this.tokens = tokens;
            this.lastRefillMillis = lastRefillMillis;
        }
    }
}
```

- [ ] **Step 2: Add Redis limiter tests**

Add `RedisRateLimiterTest.java`:

```java
package com.example.ratelimiter.distributed;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisRateLimiterTest {

    @Test
    void allowsUpToDistributedCapacity() {
        RedisRateLimiter limiter = new RedisRateLimiter("api:create-order", config(2, 0.0), new FakeRedisCommandExecutor());

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        assertThat(limiter.availablePermits()).isZero();
    }

    @Test
    void supportsBulkPermits() {
        RedisRateLimiter limiter = new RedisRateLimiter("api:bulk", config(5, 0.0), new FakeRedisCommandExecutor());

        assertThat(limiter.tryAcquire(3)).isTrue();
        assertThat(limiter.availablePermits()).isEqualTo(2);
        assertThat(limiter.tryAcquire(3)).isFalse();
    }

    @Test
    void updatesStats() {
        RedisRateLimiter limiter = new RedisRateLimiter("api:stats", config(1, 0.0), new FakeRedisCommandExecutor());

        limiter.tryAcquire();
        limiter.tryAcquire();

        assertThat(limiter.getStats().allowedRequests()).isEqualTo(1);
        assertThat(limiter.getStats().rejectedRequests()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidPermits() {
        RedisRateLimiter limiter = new RedisRateLimiter("api:invalid", config(1, 0.0), new FakeRedisCommandExecutor());

        assertThatThrownBy(() -> limiter.tryAcquire(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permits must be positive");
    }

    @Test
    void propagatesRedisCommandFailure() {
        FakeRedisCommandExecutor executor = new FakeRedisCommandExecutor();
        executor.setFailCommands(true);
        RedisRateLimiter limiter = new RedisRateLimiter("api:fail", config(1, 0.0), executor);

        assertThatThrownBy(limiter::tryAcquire)
                .isInstanceOf(RedisCommandException.class)
                .hasMessageContaining("simulated redis failure");
    }

    private static RateLimiterConfig config(long capacity, double rate) {
        return RateLimiterConfig.builder(AlgorithmType.DISTRIBUTED_TOKEN_BUCKET)
                .capacity(capacity)
                .ratePerSecond(rate)
                .window(Duration.ofSeconds(1))
                .build();
    }
}
```

- [ ] **Step 3: Add Redis limiter implementation**

Add `RedisRateLimiter.java`:

```java
package com.example.ratelimiter.distributed;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class RedisRateLimiter implements RateLimiter {

    private final String key;
    private final RedisCommandExecutor redis;
    private volatile RateLimiterConfig config;
    private final AtomicLong allowedRequests = new AtomicLong();
    private final AtomicLong rejectedRequests = new AtomicLong();
    private final AtomicLong availablePermits = new AtomicLong();

    public RedisRateLimiter(String key, RateLimiterConfig config, RedisCommandExecutor redis) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.availablePermits.set(config.capacity());
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
        RateLimiterConfig current = config;
        List<Long> result = redis.evalTokenBucket(
                key,
                current.capacity(),
                current.ratePerSecond(),
                permits,
                System.currentTimeMillis()
        );
        boolean allowed = result.get(0) == 1;
        availablePermits.set(result.get(1));
        if (allowed) {
            allowedRequests.incrementAndGet();
            return true;
        }
        rejectedRequests.incrementAndGet();
        return false;
    }

    @Override
    public long availablePermits() {
        return availablePermits.get();
    }

    @Override
    public RateLimiterStats getStats() {
        return new RateLimiterStats(allowedRequests.get(), rejectedRequests.get(), availablePermits.get());
    }

    @Override
    public void updateConfig(RateLimiterConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.availablePermits.set(config.capacity());
    }
}
```

- [ ] **Step 4: Run tests**

Run:

```powershell
mvn test -Dtest=RedisRateLimiterTest
```

Expected: Redis limiter tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add src/main/java/com/example/ratelimiter/distributed/RedisRateLimiter.java src/test/java/com/example/ratelimiter/distributed
git commit -m "feat: implement redis token bucket limiter"
```

## Task 4: Add Health Check and Degrading Rate Limiter

**Files:**

- Create: `src/main/java/com/example/ratelimiter/distributed/RedisHealthChecker.java`
- Create: `src/main/java/com/example/ratelimiter/distributed/DegradingRateLimiter.java`
- Create: `src/test/java/com/example/ratelimiter/distributed/DegradingRateLimiterTest.java`

- [ ] **Step 1: Add health checker**

Add `RedisHealthChecker.java`:

```java
package com.example.ratelimiter.distributed;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class RedisHealthChecker {

    private final RedisCommandExecutor redis;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    public RedisHealthChecker(RedisCommandExecutor redis) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
    }

    public boolean refresh() {
        boolean current = redis.ping();
        healthy.set(current);
        return current;
    }

    public boolean isHealthy() {
        return healthy.get();
    }

    public void markUnhealthy() {
        healthy.set(false);
    }
}
```

- [ ] **Step 2: Add degrading limiter tests**

Add `DegradingRateLimiterTest.java`:

```java
package com.example.ratelimiter.distributed;

import com.example.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DegradingRateLimiterTest {

    @Test
    void usesRedisWhenHealthy() {
        FakeRedisCommandExecutor executor = new FakeRedisCommandExecutor();
        RedisHealthChecker healthChecker = new RedisHealthChecker(executor);
        DegradingRateLimiter limiter = limiter(executor, healthChecker, 1, 10);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        assertThat(limiter.getStats().allowedRequests()).isEqualTo(1);
        assertThat(limiter.getStats().rejectedRequests()).isEqualTo(1);
    }

    @Test
    void fallsBackToLocalWhenRedisUnhealthy() {
        FakeRedisCommandExecutor executor = new FakeRedisCommandExecutor();
        RedisHealthChecker healthChecker = new RedisHealthChecker(executor);
        healthChecker.markUnhealthy();
        DegradingRateLimiter limiter = limiter(executor, healthChecker, 1, 2);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void marksRedisUnhealthyWhenDistributedAcquireThrows() {
        FakeRedisCommandExecutor executor = new FakeRedisCommandExecutor();
        executor.setFailCommands(true);
        RedisHealthChecker healthChecker = new RedisHealthChecker(executor);
        DegradingRateLimiter limiter = limiter(executor, healthChecker, 1, 2);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(healthChecker.isHealthy()).isFalse();
    }

    @Test
    void refreshCanRestoreRedisHealth() {
        FakeRedisCommandExecutor executor = new FakeRedisCommandExecutor();
        RedisHealthChecker healthChecker = new RedisHealthChecker(executor);
        healthChecker.markUnhealthy();

        assertThat(healthChecker.refresh()).isTrue();
        assertThat(healthChecker.isHealthy()).isTrue();
    }

    private static DegradingRateLimiter limiter(
            FakeRedisCommandExecutor executor,
            RedisHealthChecker healthChecker,
            long distributedCapacity,
            long localCapacity) {
        RedisRateLimiter distributed = new RedisRateLimiter(
                "api:degrade",
                config(AlgorithmType.DISTRIBUTED_TOKEN_BUCKET, distributedCapacity),
                executor
        );
        TokenBucketRateLimiter local = new TokenBucketRateLimiter(config(AlgorithmType.TOKEN_BUCKET, localCapacity));
        return new DegradingRateLimiter(distributed, local, healthChecker);
    }

    private static RateLimiterConfig config(AlgorithmType algorithm, long capacity) {
        return RateLimiterConfig.builder(algorithm)
                .capacity(capacity)
                .ratePerSecond(0.0)
                .window(Duration.ofSeconds(1))
                .build();
    }
}
```

- [ ] **Step 3: Add degrading limiter**

Add `DegradingRateLimiter.java`:

```java
package com.example.ratelimiter.distributed;

import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.stats.RateLimiterStats;

import java.util.Objects;

public class DegradingRateLimiter implements RateLimiter {

    private final RateLimiter distributed;
    private final RateLimiter localFallback;
    private final RedisHealthChecker healthChecker;

    public DegradingRateLimiter(RateLimiter distributed, RateLimiter localFallback, RedisHealthChecker healthChecker) {
        this.distributed = Objects.requireNonNull(distributed, "distributed must not be null");
        this.localFallback = Objects.requireNonNull(localFallback, "localFallback must not be null");
        this.healthChecker = Objects.requireNonNull(healthChecker, "healthChecker must not be null");
    }

    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    @Override
    public boolean tryAcquire(int permits) {
        if (!healthChecker.isHealthy()) {
            return localFallback.tryAcquire(permits);
        }
        try {
            return distributed.tryAcquire(permits);
        } catch (RedisCommandException ex) {
            healthChecker.markUnhealthy();
            return localFallback.tryAcquire(permits);
        }
    }

    @Override
    public long availablePermits() {
        if (!healthChecker.isHealthy()) {
            return localFallback.availablePermits();
        }
        return distributed.availablePermits();
    }

    @Override
    public RateLimiterStats getStats() {
        if (!healthChecker.isHealthy()) {
            return localFallback.getStats();
        }
        return distributed.getStats();
    }

    @Override
    public void updateConfig(RateLimiterConfig config) {
        distributed.updateConfig(config);
        localFallback.updateConfig(config);
    }
}
```

- [ ] **Step 4: Run tests**

Run:

```powershell
mvn test -Dtest=RedisRateLimiterTest,DegradingRateLimiterTest
```

Expected: Redis limiter and degradation tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add src/main/java/com/example/ratelimiter/distributed src/test/java/com/example/ratelimiter/distributed
git commit -m "feat: add redis degradation fallback"
```

## Task 5: Wire Factory and Documentation

**Files:**

- Modify: `src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java`
- Modify: `src/test/java/com/example/ratelimiter/core/RateLimiterFactoryTest.java`
- Modify: `README.md`

- [ ] **Step 1: Add factory behavior test for unsupported distributed creation**

Modify `RateLimiterFactoryTest.java` to assert the default factory rejects distributed Redis configs because it needs a Redis executor:

```java
@Test
void rejectsDistributedLimiterWithoutRedisExecutor() {
    assertThatThrownBy(() -> factory.getOrCreate("distributed", config(AlgorithmType.DISTRIBUTED_TOKEN_BUCKET)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("distributed token bucket requires RedisRateLimiter");
}
```

Add import:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 2: Update factory switch**

Modify `RateLimiterFactory.java`:

```java
case DISTRIBUTED_TOKEN_BUCKET -> throw new IllegalArgumentException(
        "distributed token bucket requires RedisRateLimiter with a RedisCommandExecutor"
);
```

- [ ] **Step 3: Document distributed limiter**

Add this section to `README.md`:

```markdown
## 分布式限流

Phase 3 引入了基于 Redis Lua 的分布式令牌桶：

- Redis Lua 保证 refill + acquire 的原子性。
- `RedisRateLimiter` 实现和单机限流器相同的 `RateLimiter` 接口。
- `DegradingRateLimiter` 在 Redis 不可用或命令失败时降级到本地令牌桶。
- `RedisHealthChecker` 通过 `PING` 维护 Redis 健康状态。

当前工厂 `RateLimiterFactory` 只创建单机限流器。分布式限流器需要显式传入 `RedisCommandExecutor`：

```java
RedisCommandExecutor redis = new SpringDataRedisCommandExecutor(stringRedisTemplate);
RateLimiterConfig config = RateLimiterConfig.builder(AlgorithmType.DISTRIBUTED_TOKEN_BUCKET)
        .capacity(1000)
        .ratePerSecond(100.0)
        .window(Duration.ofSeconds(1))
        .build();

RateLimiter limiter = new RedisRateLimiter("api:create-order", config, redis);
```

Redis 故障时可以组合本地降级：

```java
RateLimiter localFallback = new TokenBucketRateLimiter(config.toBuilder()
        .capacity(100)
        .ratePerSecond(10.0)
        .build());
RateLimiter limiter = new DegradingRateLimiter(distributedLimiter, localFallback, redisHealthChecker);
```
```

- [ ] **Step 4: Run full tests**

Run:

```powershell
mvn test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java src/test/java/com/example/ratelimiter/core/RateLimiterFactoryTest.java README.md
git commit -m "docs: document redis distributed limiter"
```

## Task 6: Final Phase 3 Verification

**Files:**

- No source file changes expected.

- [ ] **Step 1: Run full verification**

Run:

```powershell
mvn test
mvn -Pbenchmark -DskipTests package
java -jar target/benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
git status --short
```

Expected:

- Unit test suite passes.
- Benchmark jar still builds.
- JMH smoke benchmark still runs.
- Working tree is clean.

- [ ] **Step 2: Push**

Run:

```powershell
git push
```

Expected: local `main` pushes to `origin/main`.

## Self-Review Checklist

Spec coverage:

- Redis Lua token bucket script is included.
- Java distributed limiter implements existing `RateLimiter` interface.
- Redis health check exists.
- Redis command failure degrades to local limiter.
- Tests cover Redis limiter, failure propagation, degradation, and health restore.
- Factory behavior is explicit instead of silently creating a broken distributed limiter without Redis dependencies.

Verification:

- Unit tests pass.
- Existing local algorithms still pass.
- Benchmark packaging remains healthy after adding Redis dependencies.

Scope boundaries:

- No Redis cluster quota redistribution in this phase.
- No Testcontainers requirement in this phase; unit tests use a fake Redis executor.
- No Spring Boot auto-configuration in this phase.
- No dashboard integration in this phase.
