# Phase 6.1 Guava Comparison Benchmark Design

## Goal

Add a lightweight, reproducible benchmark entry that compares the project's local token bucket limiter with Guava's `RateLimiter`.

## Scope

- Add Guava only to the Maven `benchmark` profile so the normal application/runtime dependency set stays unchanged.
- Add a JMH benchmark class under `src/jmh/java/com/example/ratelimiter/benchmark`.
- Compare:
  - `TokenBucketRateLimiter.tryAcquire()`
  - `com.google.common.util.concurrent.RateLimiter.tryAcquire()`
- Document the benchmark command and interpretation in `README.md`.

## Non-Goals

- Do not add Sentinel in this phase.
- Do not claim one limiter is generally faster or better from a smoke benchmark.
- Do not change existing limiter behavior.
- Do not add benchmark result numbers to README unless they come from a local run.

## Design Notes

The benchmark intentionally uses a very high configured rate for both implementations so the smoke benchmark mostly measures local acquire-path overhead rather than waiting behavior. This makes it useful as a reference entry point, but the semantics are still not identical: Guava uses its own smooth rate limiter model, while this project currently benchmarks a direct token bucket implementation.

## Verification

- `mvn -f .worktrees\guava-comparison\pom.xml test`
- `mvn -f .worktrees\guava-comparison\pom.xml -Pbenchmark -DskipTests package`
- `java -jar .worktrees\guava-comparison\target\benchmarks.jar ComparisonRateLimiterBenchmark -wi 1 -i 1 -f 1`
