# Phase 6.3 Metrics REST API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose current in-process limiter statistics through `GET /api/ratelimit/stats`.

**Architecture:** Add Spring MVC support, expose immutable factory snapshots, map them through a metrics service, and return JSON from a thin REST controller. This is the data-source foundation for a later dashboard phase.

**Tech Stack:** Java 17, Spring Boot 3.3.5, Spring MVC, JUnit 5, MockMvc.

---

## File Structure

Create or modify:

```text
pom.xml
README.md
src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java
src/main/java/com/example/ratelimiter/metrics/RateLimiterMetricsController.java
src/main/java/com/example/ratelimiter/metrics/RateLimiterMetricsService.java
src/main/java/com/example/ratelimiter/metrics/RateLimiterMetricsSnapshot.java
src/test/java/com/example/ratelimiter/core/RateLimiterFactoryTest.java
src/test/java/com/example/ratelimiter/metrics/RateLimiterMetricsControllerTest.java
src/test/java/com/example/ratelimiter/metrics/RateLimiterMetricsServiceTest.java
```

Responsibilities:

- `pom.xml`: adds `spring-boot-starter-web`.
- `RateLimiterFactory`: remains the limiter owner and exposes read-only stats snapshots.
- `RateLimiterMetricsSnapshot`: immutable REST/service DTO.
- `RateLimiterMetricsService`: reads snapshots from the factory.
- `RateLimiterMetricsController`: exposes `/api/ratelimit/stats`.
- Tests cover snapshot behavior, service delegation, and REST JSON output.

## Task 1: Factory Stats Snapshots

**Files:**

- Modify: `src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java`
- Modify: `src/test/java/com/example/ratelimiter/core/RateLimiterFactoryTest.java`
- Create: `src/main/java/com/example/ratelimiter/metrics/RateLimiterMetricsSnapshot.java`

- [ ] **Step 1: Add failing factory snapshot tests**

Add imports to `RateLimiterFactoryTest`:

```java
import com.example.ratelimiter.metrics.RateLimiterMetricsSnapshot;

import java.util.Map;
```

Add tests:

```java
    @Test
    void snapshotStatsReturnsStatsForCreatedLimiters() {
        RateLimiter limiter = factory.getOrCreate("orders", config(AlgorithmType.TOKEN_BUCKET));
        limiter.tryAcquire();

        Map<String, RateLimiterMetricsSnapshot> snapshots = factory.snapshotStats();

        assertThat(snapshots).containsKey("orders");
        RateLimiterMetricsSnapshot snapshot = snapshots.get("orders");
        assertThat(snapshot.key()).isEqualTo("orders");
        assertThat(snapshot.allowedRequests()).isEqualTo(1);
        assertThat(snapshot.rejectedRequests()).isZero();
        assertThat(snapshot.availablePermits()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void snapshotStatsReturnsEmptyMapWhenNoLimitersExist() {
        RateLimiterFactory emptyFactory = new RateLimiterFactory();

        assertThat(emptyFactory.snapshotStats()).isEmpty();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
mvn -f .worktrees\metrics-rest\pom.xml test -Dtest=RateLimiterFactoryTest
```

Expected: compilation fails because `RateLimiterMetricsSnapshot` and `snapshotStats()` do not exist.

- [ ] **Step 3: Add metrics snapshot record**

Create `src/main/java/com/example/ratelimiter/metrics/RateLimiterMetricsSnapshot.java`:

```java
package com.example.ratelimiter.metrics;

public record RateLimiterMetricsSnapshot(
        String key,
        long allowedRequests,
        long rejectedRequests,
        long availablePermits
) {
}
```

- [ ] **Step 4: Add factory snapshot method**

Add imports to `RateLimiterFactory`:

```java
import com.example.ratelimiter.metrics.RateLimiterMetricsSnapshot;
import com.example.ratelimiter.stats.RateLimiterStats;
import java.util.Collections;
import java.util.HashMap;
```

Add method:

```java
    public Map<String, RateLimiterMetricsSnapshot> snapshotStats() {
        Map<String, RateLimiterMetricsSnapshot> snapshots = new HashMap<>();
        registry.forEach((key, limiter) -> {
            RateLimiterStats stats = limiter.getStats();
            snapshots.put(key, new RateLimiterMetricsSnapshot(
                    key,
                    stats.allowedRequests(),
                    stats.rejectedRequests(),
                    stats.availablePermits()
            ));
        });
        return Collections.unmodifiableMap(snapshots);
    }
```

- [ ] **Step 5: Run factory tests**

Run:

```powershell
mvn -f .worktrees\metrics-rest\pom.xml test -Dtest=RateLimiterFactoryTest
```

Expected: all factory tests pass.

## Task 2: Metrics Service

**Files:**

- Create: `src/main/java/com/example/ratelimiter/metrics/RateLimiterMetricsService.java`
- Create: `src/test/java/com/example/ratelimiter/metrics/RateLimiterMetricsServiceTest.java`

- [ ] **Step 1: Add failing service tests**

Create `src/test/java/com/example/ratelimiter/metrics/RateLimiterMetricsServiceTest.java`:

```java
package com.example.ratelimiter.metrics;

import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.core.RateLimiterFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterMetricsServiceTest {

    @Test
    void snapshotsReturnsFactoryStatsSortedByKey() {
        RateLimiterFactory factory = new RateLimiterFactory();
        RateLimiter second = factory.getOrCreate("z-second", config());
        RateLimiter first = factory.getOrCreate("a-first", config());
        first.tryAcquire();
        second.tryAcquire();
        second.tryAcquire();

        RateLimiterMetricsService service = new RateLimiterMetricsService(factory);

        List<RateLimiterMetricsSnapshot> snapshots = service.snapshots();

        assertThat(snapshots).extracting(RateLimiterMetricsSnapshot::key)
                .containsExactly("a-first", "z-second");
        assertThat(snapshots.get(0).allowedRequests()).isEqualTo(1);
        assertThat(snapshots.get(1).allowedRequests()).isEqualTo(2);
    }

    private static RateLimiterConfig config() {
        return RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                .capacity(100)
                .ratePerSecond(100.0)
                .window(Duration.ofSeconds(1))
                .build();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\metrics-rest\pom.xml test -Dtest=RateLimiterMetricsServiceTest
```

Expected: compilation fails because `RateLimiterMetricsService` does not exist.

- [ ] **Step 3: Add service**

Create `src/main/java/com/example/ratelimiter/metrics/RateLimiterMetricsService.java`:

```java
package com.example.ratelimiter.metrics;

import com.example.ratelimiter.core.RateLimiterFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class RateLimiterMetricsService {

    private final RateLimiterFactory factory;

    public RateLimiterMetricsService(RateLimiterFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
    }

    public List<RateLimiterMetricsSnapshot> snapshots() {
        return factory.snapshotStats().values().stream()
                .sorted(Comparator.comparing(RateLimiterMetricsSnapshot::key))
                .toList();
    }
}
```

- [ ] **Step 4: Run service test**

Run:

```powershell
mvn -f .worktrees\metrics-rest\pom.xml test -Dtest=RateLimiterMetricsServiceTest
```

Expected: service test passes.

## Task 3: REST Controller

**Files:**

- Modify: `pom.xml`
- Create: `src/main/java/com/example/ratelimiter/metrics/RateLimiterMetricsController.java`
- Create: `src/test/java/com/example/ratelimiter/metrics/RateLimiterMetricsControllerTest.java`

- [ ] **Step 1: Add Spring Web dependency**

Add dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

- [ ] **Step 2: Add failing controller test**

Create `src/test/java/com/example/ratelimiter/metrics/RateLimiterMetricsControllerTest.java`:

```java
package com.example.ratelimiter.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RateLimiterMetricsController.class)
class RateLimiterMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimiterMetricsService service;

    @Test
    void statsReturnsLimiterSnapshots() throws Exception {
        when(service.snapshots()).thenReturn(List.of(
                new RateLimiterMetricsSnapshot("orders", 3, 1, 96)
        ));

        mockMvc.perform(get("/api/ratelimit/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("orders"))
                .andExpect(jsonPath("$[0].allowedRequests").value(3))
                .andExpect(jsonPath("$[0].rejectedRequests").value(1))
                .andExpect(jsonPath("$[0].availablePermits").value(96));
    }

    @Test
    void statsReturnsEmptyArrayWhenNoLimitersExist() throws Exception {
        when(service.snapshots()).thenReturn(List.of());

        mockMvc.perform(get("/api/ratelimit/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
```

- [ ] **Step 3: Run controller test to verify it fails**

Run:

```powershell
mvn -f .worktrees\metrics-rest\pom.xml test -Dtest=RateLimiterMetricsControllerTest
```

Expected: compilation fails because `RateLimiterMetricsController` does not exist.

- [ ] **Step 4: Add controller**

Create `src/main/java/com/example/ratelimiter/metrics/RateLimiterMetricsController.java`:

```java
package com.example.ratelimiter.metrics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ratelimit")
public class RateLimiterMetricsController {

    private final RateLimiterMetricsService service;

    public RateLimiterMetricsController(RateLimiterMetricsService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public List<RateLimiterMetricsSnapshot> stats() {
        return service.snapshots();
    }
}
```

- [ ] **Step 5: Run controller test**

Run:

```powershell
mvn -f .worktrees\metrics-rest\pom.xml test -Dtest=RateLimiterMetricsControllerTest
```

Expected: controller tests pass.

## Task 4: README and Final Verification

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Update README**

Update current phase to Phase 6.3 and add metrics REST API to completed list. Add a monitoring section:

````markdown
## 监控指标 REST API

Phase 6.3 增加了本进程内限流器统计快照接口。接口只统计当前 JVM 中通过 `RateLimiterFactory` 创建过的限流器。

启动应用：
```powershell
mvn spring-boot:run
```

访问指标：
```powershell
curl http://localhost:8080/api/ratelimit/stats
```

返回示例：
```json
[
  {
    "key": "order:create",
    "allowedRequests": 10,
    "rejectedRequests": 2,
    "availablePermits": 88
  }
]
```
````

- [ ] **Step 2: Run full tests**

Run:

```powershell
mvn -f .worktrees\metrics-rest\pom.xml test
```

Expected: all tests pass.

- [ ] **Step 3: Commit**

Run:

```powershell
git -C .worktrees\metrics-rest add pom.xml README.md src/main/java/com/example/ratelimiter/core/RateLimiterFactory.java src/main/java/com/example/ratelimiter/metrics src/test/java/com/example/ratelimiter/core/RateLimiterFactoryTest.java src/test/java/com/example/ratelimiter/metrics docs/superpowers/specs/2026-06-01-phase-6-metrics-rest-design.md docs/superpowers/plans/2026-06-01-phase-6-metrics-rest.md
git -C .worktrees\metrics-rest commit -m "feat: expose rate limiter metrics endpoint"
```

## Self-Review Checklist

- REST API is limited to in-process factory-managed limiters.
- No Dashboard UI is included.
- No SSE endpoint is included.
- Tests cover factory snapshots, service sorting, and controller JSON.
- README documents local access.
