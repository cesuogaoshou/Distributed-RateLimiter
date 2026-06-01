# Phase 6.1 Guava Comparison Benchmark Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Guava comparison benchmark entry without changing normal runtime dependencies or limiter behavior.

**Architecture:** Keep Guava scoped to the Maven `benchmark` profile, then add a JMH-only benchmark class that initializes the project token bucket and Guava limiter with comparable high-rate settings. README documents how to build/run the comparison and how to interpret it carefully.

**Tech Stack:** Java 17, Maven profiles, JMH 1.37, Guava `RateLimiter`.

---

## File Structure

Create or modify:

```text
pom.xml
README.md
src/jmh/java/com/example/ratelimiter/benchmark/ComparisonRateLimiterBenchmark.java
```

Responsibilities:

- `pom.xml`: adds Guava dependency only inside the `benchmark` profile.
- `ComparisonRateLimiterBenchmark.java`: exposes JMH methods for project token bucket and Guava `RateLimiter`.
- `README.md`: documents comparison commands and warns against over-interpreting smoke results.

## Task 1: Add Benchmark-Only Guava Dependency

**Files:**

- Modify: `pom.xml`

- [ ] **Step 1: Add Guava to the benchmark profile**

Add this block inside the existing `<profile><id>benchmark</id>`:

```xml
<dependencies>
    <dependency>
        <groupId>com.google.guava</groupId>
        <artifactId>guava</artifactId>
        <version>33.3.1-jre</version>
    </dependency>
</dependencies>
```

- [ ] **Step 2: Verify normal tests do not require Guava**

Run:

```powershell
mvn -f .worktrees\guava-comparison\pom.xml test
```

Expected: existing tests pass without activating the benchmark profile.

## Task 2: Add JMH Comparison Benchmark

**Files:**

- Create: `src/jmh/java/com/example/ratelimiter/benchmark/ComparisonRateLimiterBenchmark.java`

- [ ] **Step 1: Add benchmark class**

Create `src/jmh/java/com/example/ratelimiter/benchmark/ComparisonRateLimiterBenchmark.java`:

```java
package com.example.ratelimiter.benchmark;

import com.example.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class ComparisonRateLimiterBenchmark {

    @Benchmark
    @Threads(1)
    public boolean projectTokenBucketSingleThread(BenchmarkState state) {
        return state.projectTokenBucket.tryAcquire();
    }

    @Benchmark
    @Threads(1)
    public boolean guavaRateLimiterSingleThread(BenchmarkState state) {
        return state.guavaRateLimiter.tryAcquire();
    }

    @Benchmark
    @Threads(4)
    public boolean projectTokenBucketFourThreads(BenchmarkState state) {
        return state.projectTokenBucket.tryAcquire();
    }

    @Benchmark
    @Threads(4)
    public boolean guavaRateLimiterFourThreads(BenchmarkState state) {
        return state.guavaRateLimiter.tryAcquire();
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        private static final long CAPACITY = 100_000_000L;
        private static final double RATE_PER_SECOND = 100_000_000.0;

        private RateLimiter projectTokenBucket;
        private com.google.common.util.concurrent.RateLimiter guavaRateLimiter;

        @Setup(Level.Trial)
        public void setup() {
            projectTokenBucket = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                    .capacity(CAPACITY)
                    .ratePerSecond(RATE_PER_SECOND)
                    .window(Duration.ofSeconds(1))
                    .build());
            guavaRateLimiter = com.google.common.util.concurrent.RateLimiter.create(RATE_PER_SECOND);
        }
    }
}
```

- [ ] **Step 2: Build benchmark jar**

Run:

```powershell
mvn -f .worktrees\guava-comparison\pom.xml -Pbenchmark -DskipTests package
```

Expected: `BUILD SUCCESS`, and `target\benchmarks.jar` exists.

- [ ] **Step 3: Run comparison smoke benchmark**

Run:

```powershell
java -jar .worktrees\guava-comparison\target\benchmarks.jar ComparisonRateLimiterBenchmark -wi 1 -i 1 -f 1
```

Expected: JMH prints result rows for project and Guava methods.

## Task 3: Document Comparison Entry

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Update project status**

Change current phase to Phase 6.1 and add Guava comparison benchmark to the completed list.

- [ ] **Step 2: Add comparison benchmark docs**

Add a README section after the JMH benchmark section:

````markdown
## Guava 对比 benchmark

Phase 6.1 增加了一个轻量级 Guava 对比入口。Guava 只在 `benchmark` profile 中引入，不会进入普通应用运行依赖。

构建 benchmark jar：
```powershell
mvn -Pbenchmark -DskipTests package
```

运行 Guava 对比 benchmark：
```powershell
java -jar target/benchmarks.jar ComparisonRateLimiterBenchmark
```

快速 smoke test：
```powershell
java -jar target/benchmarks.jar ComparisonRateLimiterBenchmark -wi 1 -i 1 -f 1
```

这个 benchmark 用于提供本项目 Token Bucket 与 Guava `RateLimiter` 的本地参考对比。两者实现语义并不完全相同，README 中不写没有本机实测来源的性能结论。
````

- [ ] **Step 3: Run final verification**

Run:

```powershell
mvn -f .worktrees\guava-comparison\pom.xml test
mvn -f .worktrees\guava-comparison\pom.xml -Pbenchmark -DskipTests package
java -jar .worktrees\guava-comparison\target\benchmarks.jar ComparisonRateLimiterBenchmark -wi 1 -i 1 -f 1
```

Expected: tests pass, benchmark jar builds, and JMH smoke completes.

- [ ] **Step 4: Commit**

Run:

```powershell
git -C .worktrees\guava-comparison add pom.xml README.md src/jmh/java/com/example/ratelimiter/benchmark/ComparisonRateLimiterBenchmark.java docs/superpowers/specs/2026-06-01-phase-6-guava-comparison-design.md docs/superpowers/plans/2026-06-01-phase-6-guava-comparison.md
git -C .worktrees\guava-comparison commit -m "feat: add guava comparison benchmark"
```

## Self-Review Checklist

- Guava is scoped to the benchmark profile only.
- Existing runtime dependencies are unchanged.
- JMH benchmark has project and Guava methods for single-thread and four-thread smoke coverage.
- README documents commands and comparison limitations.
- No invented performance numbers are added.
