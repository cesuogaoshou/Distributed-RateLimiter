# Phase 6.2 Sentinel Comparison Benchmark Design

## Goal

Add a lightweight Sentinel comparison benchmark entry beside the existing project and Guava benchmark methods.

## Scope

- Add `com.alibaba.csp:sentinel-core` only to the Maven `benchmark` profile.
- Extend `ComparisonRateLimiterBenchmark` with Sentinel single-thread and four-thread methods.
- Configure a Sentinel `FlowRule` during JMH setup with a high QPS threshold.
- Treat Sentinel as a benchmark/reference dependency only.
- Document commands and interpretation in `README.md`.

## Non-Goals

- Do not integrate Sentinel with `@RateLimit`.
- Do not add Sentinel Dashboard.
- Do not add Spring Cloud Alibaba Sentinel.
- Do not change existing local limiter behavior.
- Do not write benchmark result claims without local evidence.

## Design Notes

Sentinel uses a resource and rule model. The benchmark creates a resource name, loads a high-threshold QPS rule through `FlowRuleManager`, and measures `SphU.entry(resource)` plus `entry.exit()` on the allowed path. If Sentinel blocks the call, the benchmark returns `false` instead of throwing, matching the boolean shape of the existing limiter benchmarks.

This benchmark is useful for a rough call-path reference only. Sentinel provides broader traffic governance features than a local token bucket, so README must avoid simple performance superiority claims.

## Verification

- `mvn -f .worktrees\sentinel-comparison\pom.xml test`
- `mvn -f .worktrees\sentinel-comparison\pom.xml -Pbenchmark -DskipTests package`
- `java -jar .worktrees\sentinel-comparison\target\benchmarks.jar ComparisonRateLimiterBenchmark.sentinel -wi 1 -i 1 -f 1`
