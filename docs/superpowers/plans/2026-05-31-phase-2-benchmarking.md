# Phase 2 Benchmarking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reproducible JMH benchmarks for the four local rate limiter algorithms and document how to run and interpret the results.

**Architecture:** Keep benchmarks isolated from production and unit-test code by adding a dedicated Maven benchmark profile that compiles `src/jmh/java`. Benchmarks instantiate the existing local rate limiters directly with equivalent configs so throughput and contention results are comparable.

**Tech Stack:** Java 17 target bytecode, Maven, JMH 1.37, existing local limiter implementations, README benchmark documentation.

---

## File Structure

Create or modify:

```text
pom.xml
README.md
src/jmh/java/com/example/ratelimiter/benchmark/LocalRateLimiterBenchmark.java
```

Responsibilities:

- `pom.xml`: owns JMH dependency and benchmark profile only.
- `LocalRateLimiterBenchmark`: contains comparable JMH benchmark methods for token bucket, leaky bucket, fixed window, and sliding window.
- `README.md`: explains benchmark commands and how to record measured results without inventing data.

## Task 1: Add JMH Build Profile

**Files:**

- Modify: `pom.xml`

- [ ] **Step 1: Add JMH properties and benchmark profile**

Add these properties under existing `<properties>`:

```xml
<jmh.version>1.37</jmh.version>
<jmh.generated.sources>${project.build.directory}/generated-sources/annotations</jmh.generated.sources>
```

Add this dependency under existing `<dependencies>`:

```xml
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>${jmh.version}</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>${jmh.version}</version>
    <scope>provided</scope>
</dependency>
```

Add a `benchmark` Maven profile under the root `<project>`:

```xml
<profiles>
    <profile>
        <id>benchmark</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.codehaus.mojo</groupId>
                    <artifactId>build-helper-maven-plugin</artifactId>
                    <version>3.6.0</version>
                    <executions>
                        <execution>
                            <id>add-jmh-source</id>
                            <phase>generate-sources</phase>
                            <goals>
                                <goal>add-source</goal>
                            </goals>
                            <configuration>
                                <sources>
                                    <source>src/jmh/java</source>
                                </sources>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-shade-plugin</artifactId>
                    <version>3.6.0</version>
                    <executions>
                        <execution>
                            <phase>package</phase>
                            <goals>
                                <goal>shade</goal>
                            </goals>
                            <configuration>
                                <finalName>benchmarks</finalName>
                                <transformers>
                                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                        <mainClass>org.openjdk.jmh.Main</mainClass>
                                    </transformer>
                                    <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                                </transformers>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

- [ ] **Step 2: Run normal tests**

Run:

```powershell
mvn test
```

Expected: existing 27 tests pass, confirming JMH dependencies did not disturb normal test execution.

- [ ] **Step 3: Commit**

Run:

```powershell
git add pom.xml
git commit -m "build: add jmh benchmark profile"
```

## Task 2: Add Local Limiter Benchmarks

**Files:**

- Create: `src/jmh/java/com/example/ratelimiter/benchmark/LocalRateLimiterBenchmark.java`

- [ ] **Step 1: Create benchmark class**

Add `LocalRateLimiterBenchmark.java`:

```java
package com.example.ratelimiter.benchmark;

import com.example.ratelimiter.algorithm.FixedWindowRateLimiter;
import com.example.ratelimiter.algorithm.LeakyBucketRateLimiter;
import com.example.ratelimiter.algorithm.SlidingWindowRateLimiter;
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
public class LocalRateLimiterBenchmark {

    @Benchmark
    @Threads(1)
    public boolean tokenBucketSingleThread(BenchmarkState state) {
        return state.tokenBucket.tryAcquire();
    }

    @Benchmark
    @Threads(4)
    public boolean tokenBucketFourThreads(BenchmarkState state) {
        return state.tokenBucket.tryAcquire();
    }

    @Benchmark
    @Threads(8)
    public boolean tokenBucketEightThreads(BenchmarkState state) {
        return state.tokenBucket.tryAcquire();
    }

    @Benchmark
    @Threads(1)
    public boolean leakyBucketSingleThread(BenchmarkState state) {
        return state.leakyBucket.tryAcquire();
    }

    @Benchmark
    @Threads(4)
    public boolean leakyBucketFourThreads(BenchmarkState state) {
        return state.leakyBucket.tryAcquire();
    }

    @Benchmark
    @Threads(8)
    public boolean leakyBucketEightThreads(BenchmarkState state) {
        return state.leakyBucket.tryAcquire();
    }

    @Benchmark
    @Threads(1)
    public boolean fixedWindowSingleThread(BenchmarkState state) {
        return state.fixedWindow.tryAcquire();
    }

    @Benchmark
    @Threads(4)
    public boolean fixedWindowFourThreads(BenchmarkState state) {
        return state.fixedWindow.tryAcquire();
    }

    @Benchmark
    @Threads(8)
    public boolean fixedWindowEightThreads(BenchmarkState state) {
        return state.fixedWindow.tryAcquire();
    }

    @Benchmark
    @Threads(1)
    public boolean slidingWindowSingleThread(BenchmarkState state) {
        return state.slidingWindow.tryAcquire();
    }

    @Benchmark
    @Threads(4)
    public boolean slidingWindowFourThreads(BenchmarkState state) {
        return state.slidingWindow.tryAcquire();
    }

    @Benchmark
    @Threads(8)
    public boolean slidingWindowEightThreads(BenchmarkState state) {
        return state.slidingWindow.tryAcquire();
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        private RateLimiter tokenBucket;
        private RateLimiter leakyBucket;
        private RateLimiter fixedWindow;
        private RateLimiter slidingWindow;

        @Setup(Level.Trial)
        public void setup() {
            tokenBucket = new TokenBucketRateLimiter(config(AlgorithmType.TOKEN_BUCKET));
            leakyBucket = new LeakyBucketRateLimiter(config(AlgorithmType.LEAKY_BUCKET));
            fixedWindow = new FixedWindowRateLimiter(config(AlgorithmType.FIXED_WINDOW));
            slidingWindow = new SlidingWindowRateLimiter(config(AlgorithmType.SLIDING_WINDOW));
        }

        private static RateLimiterConfig config(AlgorithmType algorithm) {
            return RateLimiterConfig.builder(algorithm)
                    .capacity(100_000_000)
                    .ratePerSecond(100_000_000.0)
                    .window(Duration.ofSeconds(1))
                    .build();
        }
    }
}
```

Rationale: the high capacity and high refill rate reduce benchmark noise from intentional rejection paths. Phase 2 measures local method overhead and contention, not realistic production throttling.

- [ ] **Step 2: Compile benchmark jar**

Run:

```powershell
mvn -Pbenchmark -DskipTests package
```

Expected: build succeeds and creates `target/benchmarks.jar`.

- [ ] **Step 3: Run one quick benchmark smoke test**

Run:

```powershell
java -jar target/benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
```

Expected: JMH runs one benchmark and prints a throughput score in `ops/s`.

- [ ] **Step 4: Commit**

Run:

```powershell
git add src/jmh/java/com/example/ratelimiter/benchmark/LocalRateLimiterBenchmark.java
git commit -m "test: add local rate limiter jmh benchmarks"
```

## Task 3: Document Benchmark Workflow

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Add benchmark documentation**

Add this section to `README.md`:

```markdown
## JMH 基准测试

构建 benchmark jar：

```powershell
mvn -Pbenchmark -DskipTests package
```

运行全部本地限流 benchmark：

```powershell
java -jar target/benchmarks.jar LocalRateLimiterBenchmark
```

快速 smoke test：

```powershell
java -jar target/benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
```

当前 benchmark 覆盖：

| 算法 | 线程维度 |
|------|----------|
| Token Bucket | 1 / 4 / 8 |
| Leaky Bucket | 1 / 4 / 8 |
| Fixed Window | 1 / 4 / 8 |
| Sliding Window | 1 / 4 / 8 |

README 中的性能数据必须来自本机实际运行结果，不写虚构数据。后续与 Guava/Sentinel 的对比会单独扩展。
```

- [ ] **Step 2: Run verification commands**

Run:

```powershell
mvn test
mvn -Pbenchmark -DskipTests package
java -jar target/benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
git status --short
```

Expected:

- `mvn test` passes.
- Benchmark package succeeds.
- Smoke benchmark prints one throughput score.
- `git status --short` shows only `README.md` modified.

- [ ] **Step 3: Commit**

Run:

```powershell
git add README.md
git commit -m "docs: document jmh benchmark workflow"
```

## Task 4: Final Phase 2 Verification

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
- Benchmark jar builds.
- JMH smoke benchmark completes successfully.
- Working tree is clean.

- [ ] **Step 2: Push**

Run:

```powershell
git push
```

Expected: local `main` pushes to `origin/main`.

## Self-Review Checklist

Spec coverage:

- Adds JMH benchmark infrastructure.
- Covers single-thread, 4-thread, and 8-thread dimensions for all four local algorithms.
- Documents benchmark build and run commands.
- Keeps Guava and Sentinel comparison deferred, as planned.

Verification:

- Normal tests still pass.
- Benchmark jar can be packaged.
- At least one benchmark can execute end to end.

Scope boundaries:

- No Redis implementation.
- No adaptive limiter implementation.
- No dashboard implementation.
- No invented benchmark numbers in README.
