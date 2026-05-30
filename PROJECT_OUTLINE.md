# Distributed RateLimiter Project Outline

> This outline is the working roadmap for this repository. The original full
> specification lives in `Distributed-RateLimiter-Spec.md`; future work should
> follow this outline first, then refine each phase into implementation plans.

## 1. Project Goal

Build a high-performance distributed rate limiting middleware project that
demonstrates Java concurrency, rate limiting algorithms, Redis-based distributed
coordination, adaptive control, benchmarking, monitoring, and clean
Spring Boot integration.

Target stack:

- Java 17
- Spring Boot 3
- Maven
- JUnit 5
- Redis + Lua
- JMH
- Micrometer
- Simple HTML + ECharts dashboard

## 2. Guiding Principles

- Correctness before performance.
- Every algorithm must have unit tests and concurrency tests.
- Every performance claim must come from reproducible JMH data.
- Public APIs should stay small and stable.
- Distributed behavior must include a clear fallback strategy.
- README quality is part of the deliverable, not a final afterthought.

## 3. Overall Roadmap

### Phase 0: Project Initialization

Create the Java project foundation.

Deliverables:

- Java 17 + Spring Boot 3 Maven project.
- Test framework with JUnit 5.
- Basic package layout.
- Initial README with project scope and development commands.
- Optional Docker Compose placeholder for Redis.

### Phase 1: Local Rate Limiter Core

Implement the core local rate limiting engine.

Deliverables:

- `RateLimiter` interface.
- `RateLimiterConfig` configuration model.
- `AlgorithmType` enum.
- `RateLimiterStats` statistics model.
- Token bucket implementation.
- Leaky bucket implementation.
- Fixed window implementation.
- Sliding window implementation.
- `RateLimiterFactory`.
- Unit, boundary, timing, and concurrency tests for every algorithm.

Success criteria:

- All core tests pass.
- Each algorithm has clear behavior for invalid or edge parameters.
- Concurrent acquisition never violates configured limits.
- Runtime config updates are covered by tests.

### Phase 2: Benchmarking

Add reproducible benchmark coverage.

Deliverables:

- JMH benchmark module or benchmark source set.
- Single-thread throughput tests.
- 4, 8, and 16 thread contention tests.
- GC allocation profiling commands.
- Optional Guava RateLimiter comparison.
- Optional Sentinel comparison.
- Benchmark result template for README.

Success criteria:

- Benchmarks run from a documented command.
- Results are not hardcoded or invented.
- README clearly separates measured data from planned comparisons.

### Phase 3: Distributed Rate Limiting

Implement Redis-backed distributed rate limiting.

Deliverables:

- Redis Lua script for token bucket acquisition.
- Java wrapper implementing the same `RateLimiter` interface.
- Redis key model and expiration strategy.
- Redis health check.
- Degrading rate limiter that falls back to local mode.
- Tests using Testcontainers or an equivalent Redis test setup.

Success criteria:

- Lua script performs atomic refill and acquire.
- Distributed limiter shares the same public interface as local limiters.
- Redis failure has a documented and tested fallback path.

### Phase 4: Adaptive Rate Limiting

Add dynamic threshold adjustment based on runtime load.

Deliverables:

- `SystemMetrics` model.
- System metrics collector.
- PID controller.
- Adaptive scheduler.
- Config model for adaptive mode.
- Tests for PID behavior and threshold bounds.

Success criteria:

- The adaptive limiter can tighten and relax limits.
- Thresholds are clamped to configured min and max values.
- PID behavior is documented with test scenarios.

### Phase 5: Integration Layer and SPI

Make the middleware easy to integrate and extend.

Deliverables:

- `@RateLimit` annotation.
- Spring AOP aspect.
- YAML/properties rule provider.
- Java SPI interfaces:
  - `RuleProvider`
  - `RateLimiterAlgorithm`
  - `RejectHandler`
- Default reject handler.
- Integration tests for annotation and config-driven usage.

Success criteria:

- A Spring controller method can be protected with `@RateLimit`.
- Rule loading is deterministic and testable.
- SPI extensions can be added without changing core code.

### Phase 6: Monitoring Dashboard

Expose runtime visibility.

Deliverables:

- Micrometer metrics integration.
- REST metrics endpoint.
- SSE metrics stream endpoint.
- Simple dashboard page using ECharts.
- Screenshots or GIF for README.

Success criteria:

- QPS, pass count, reject count, and active limiter configs are visible.
- Dashboard can run locally with documented steps.
- Metrics endpoints are covered by tests.

### Phase 7: Documentation and Open Source Polish

Prepare the project for public review.

Deliverables:

- Complete README.
- Architecture diagram.
- Quick start guide.
- Algorithm comparison table.
- JMH benchmark report.
- Distributed design notes.
- Adaptive limiting notes.
- Sentinel and Guava comparison.
- Roadmap.
- Interview review notes.

Success criteria:

- A new reader can run the project from README alone.
- Claims are backed by code, tests, or measured benchmark output.
- Known limitations are stated honestly.

## 4. Week 1 Execution Outline

Week 1 focuses only on the local rate limiter core. Distributed Redis,
adaptive limiting, SPI, and dashboard work are intentionally deferred.

### Day 1: Project Foundation

Tasks:

- Initialize Maven/Spring Boot 3 project.
- Configure Java 17.
- Add JUnit 5 test setup.
- Create base packages:
  - `core`
  - `algorithm`
  - `config`
  - `stats`
  - `exception`
  - `testsupport`
- Add initial README.

Deliverables:

- Build runs successfully.
- One minimal smoke test passes.

### Day 2: Core API and Token Bucket

Tasks:

- Define `RateLimiter`.
- Define `RateLimiterConfig`.
- Define `AlgorithmType`.
- Define `RateLimiterStats`.
- Implement token bucket.
- Add tests for burst traffic, steady refill, bulk permits, edge cases, and concurrency.

Deliverables:

- Token bucket is usable through the common interface.
- Token bucket tests pass.

### Day 3: Leaky Bucket

Tasks:

- Implement leaky bucket.
- Add tests for smooth draining, capacity rejection, timing recovery, and concurrency.
- Document token bucket vs leaky bucket behavior in README.

Deliverables:

- Leaky bucket is usable through the common interface.
- Leaky bucket tests pass.

### Day 4: Fixed Window

Tasks:

- Implement fixed window limiter.
- Use atomic counters and safe window reset logic.
- Add tests for window boundary behavior and concurrent access.
- Document fixed window boundary burst behavior.

Deliverables:

- Fixed window limiter is usable through the common interface.
- Fixed window tests pass.

### Day 5: Sliding Window

Tasks:

- Implement exact sliding window with timestamp queue.
- Add tests for moving-window precision, eviction, rejection, and concurrency.
- Document the memory and CPU tradeoff of exact sliding windows.

Deliverables:

- Sliding window limiter is usable through the common interface.
- Sliding window tests pass.

### Day 6: Factory and Cross-Algorithm Tests

Tasks:

- Implement `RateLimiterFactory`.
- Support key-based limiter reuse.
- Add tests that create all algorithms from config.
- Add cross-algorithm behavior comparison tests.
- Add config update tests.

Deliverables:

- Factory creates and reuses limiters correctly.
- Cross-algorithm tests pass.

### Day 7: Cleanup and Documentation

Tasks:

- Review test coverage.
- Tighten README quick start.
- Add algorithm comparison table.
- Check formatting and naming consistency.
- Record known limitations for Phase 1.

Deliverables:

- Week 1 scope is documented and testable.
- The repository is ready for Phase 2 planning.

## 5. Initial Package Direction

The exact package root can be adjusted during project initialization. The
preferred structure is:

```text
com.example.ratelimiter
├── algorithm
│   ├── FixedWindowRateLimiter
│   ├── LeakyBucketRateLimiter
│   ├── SlidingWindowRateLimiter
│   └── TokenBucketRateLimiter
├── config
│   ├── AlgorithmType
│   └── RateLimiterConfig
├── core
│   ├── RateLimiter
│   └── RateLimiterFactory
├── exception
│   ├── RateLimitException
│   └── RateLimiterConfigException
├── stats
│   └── RateLimiterStats
└── testsupport
    └── ConcurrentTestSupport
```

## 6. Testing Matrix for Phase 1

Every local algorithm should cover:

- Basic acquire succeeds under limit.
- Acquire fails after limit is exhausted.
- Bulk permits behave correctly.
- Time-based recovery works.
- Concurrent callers do not exceed the configured limit.
- Invalid config is rejected with a clear exception.
- Runtime config updates affect subsequent calls.
- Statistics are updated consistently.

## 7. Deferred Scope

These items are intentionally not part of Week 1:

- Redis integration.
- Lua scripts.
- JMH benchmark implementation.
- Sentinel comparison.
- Adaptive PID scheduler.
- Spring AOP integration.
- SPI implementation.
- Dashboard UI.
- Production deployment packaging.

## 8. Next Step

After this outline is accepted, create a detailed implementation plan for
Phase 0 and Phase 1. The implementation plan should be task-based, test-first,
and specific about files, commands, and expected verification results.
