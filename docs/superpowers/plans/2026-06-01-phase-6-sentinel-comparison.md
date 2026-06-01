# Phase 6.2 Sentinel Comparison Benchmark Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Sentinel comparison benchmark entry without changing normal runtime dependencies or core rate limiter behavior.

**Architecture:** Sentinel is scoped to Maven's `benchmark` profile and only used from the JMH benchmark source set. `ComparisonRateLimiterBenchmark` initializes a high-threshold Sentinel flow rule and measures the allowed path for `SphU.entry(resource)` plus `entry.exit()`.

**Tech Stack:** Java 17, Maven profiles, JMH 1.37, Sentinel Core 1.8.8.

---

## File Structure

Create or modify:

```text
pom.xml
README.md
src/jmh/java/com/example/ratelimiter/benchmark/ComparisonRateLimiterBenchmark.java
docs/superpowers/specs/2026-06-01-phase-6-sentinel-comparison-design.md
docs/superpowers/plans/2026-06-01-phase-6-sentinel-comparison.md
```

Responsibilities:

- `pom.xml`: adds Sentinel Core only inside the `benchmark` profile.
- `ComparisonRateLimiterBenchmark.java`: adds Sentinel JMH methods beside project and Guava methods.
- `README.md`: updates phase status and documents Sentinel comparison commands and limitations.

## Task 1: Add Benchmark-Only Sentinel Dependency

**Files:**

- Modify: `pom.xml`

- [ ] **Step 1: Add Sentinel Core to the benchmark profile**

Add this dependency next to the existing Guava dependency inside the `benchmark` profile:

```xml
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-core</artifactId>
    <version>1.8.8</version>
</dependency>
```

- [ ] **Step 2: Verify normal tests do not require Sentinel**

Run:

```powershell
mvn -f .worktrees\sentinel-comparison\pom.xml test
```

Expected: existing tests pass without activating the benchmark profile.

## Task 2: Extend Comparison Benchmark

**Files:**

- Modify: `src/jmh/java/com/example/ratelimiter/benchmark/ComparisonRateLimiterBenchmark.java`

- [ ] **Step 1: Add Sentinel benchmark methods and setup**

Update `ComparisonRateLimiterBenchmark.java` to include:

```java
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import java.util.List;
```

Add benchmark methods:

```java
    @Benchmark
    @Threads(1)
    public boolean sentinelSingleThread(BenchmarkState state) {
        return state.tryAcquireSentinel();
    }

    @Benchmark
    @Threads(4)
    public boolean sentinelFourThreads(BenchmarkState state) {
        return state.tryAcquireSentinel();
    }
```

Add state fields and helper:

```java
        private static final String SENTINEL_RESOURCE = "comparison-token-bucket";

        public boolean tryAcquireSentinel() {
            Entry entry = null;
            try {
                entry = SphU.entry(SENTINEL_RESOURCE);
                return true;
            } catch (BlockException blocked) {
                return false;
            } finally {
                if (entry != null) {
                    entry.exit();
                }
            }
        }
```

Add this call in `setup()` after Guava setup:

```java
            configureSentinelRule();
```

Add rule setup:

```java
        private static void configureSentinelRule() {
            FlowRule rule = new FlowRule();
            rule.setResource(SENTINEL_RESOURCE);
            rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
            rule.setCount(RATE_PER_SECOND);
            FlowRuleManager.loadRules(List.of(rule));
        }
```

- [ ] **Step 2: Build benchmark jar**

Run:

```powershell
mvn -f .worktrees\sentinel-comparison\pom.xml -Pbenchmark -DskipTests package
```

Expected: `BUILD SUCCESS`, and `target\benchmarks.jar` exists.

- [ ] **Step 3: Run Sentinel smoke benchmark**

Run:

```powershell
java -jar .worktrees\sentinel-comparison\target\benchmarks.jar ComparisonRateLimiterBenchmark.sentinel -wi 1 -i 1 -f 1
```

Expected: JMH prints result rows for `sentinelSingleThread` and `sentinelFourThreads`.

## Task 3: Document Sentinel Comparison Entry

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Update project status**

Change current phase to Phase 6.2 and add Sentinel comparison benchmark to the completed list.

- [ ] **Step 2: Update next-step list**

Remove Sentinel comparison from next steps so the remaining next step is monitoring metrics and Dashboard.

- [ ] **Step 3: Add Sentinel docs**

Extend the comparison benchmark README section with Sentinel commands:

````markdown
## Guava / Sentinel 对比 benchmark

Phase 6.1 增加 Guava 对比入口，Phase 6.2 增加 Sentinel 对比入口。Guava 和 Sentinel 都只在 `benchmark` profile 中引入，不会进入普通应用运行依赖。

运行完整对比 benchmark：
```powershell
java -jar target/benchmarks.jar ComparisonRateLimiterBenchmark
```

只运行 Sentinel 对比 smoke test：
```powershell
java -jar target/benchmarks.jar ComparisonRateLimiterBenchmark.sentinel -wi 1 -i 1 -f 1
```

Sentinel 是资源和规则模型，benchmark 中测的是 `SphU.entry(resource)` 到 `entry.exit()` 的允许路径调用成本。它和本项目 Token Bucket、Guava `RateLimiter` 的语义并不完全相同，README 中不写没有本机实测来源的性能结论。
````

- [ ] **Step 4: Run final verification**

Run:

```powershell
mvn -f .worktrees\sentinel-comparison\pom.xml test
mvn -f .worktrees\sentinel-comparison\pom.xml -Pbenchmark -DskipTests package
java -jar .worktrees\sentinel-comparison\target\benchmarks.jar ComparisonRateLimiterBenchmark.sentinel -wi 1 -i 1 -f 1
```

Expected: tests pass, benchmark jar builds, and Sentinel JMH smoke completes.

- [ ] **Step 5: Commit**

Run:

```powershell
git -C .worktrees\sentinel-comparison add pom.xml README.md src/jmh/java/com/example/ratelimiter/benchmark/ComparisonRateLimiterBenchmark.java docs/superpowers/specs/2026-06-01-phase-6-sentinel-comparison-design.md docs/superpowers/plans/2026-06-01-phase-6-sentinel-comparison.md
git -C .worktrees\sentinel-comparison commit -m "feat: add sentinel comparison benchmark"
```

## Self-Review Checklist

- Sentinel is scoped to the benchmark profile only.
- Existing runtime dependencies are unchanged.
- JMH benchmark keeps existing project and Guava methods.
- Sentinel benchmark has single-thread and four-thread methods.
- README documents commands and comparison limitations.
- No invented benchmark result claims are added.
