# Interview Notes

This document is a concise interview guide for explaining the project.

## 30-Second Pitch

Distributed RateLimiter is a Java 17 / Spring Boot 3 rate limiting middleware project. It implements four local algorithms, a Redis Lua distributed token bucket, adaptive threshold adjustment, Spring `@RateLimit` integration, Java SPI extension points, JMH benchmark entries, REST metrics, an ECharts dashboard, and Actuator/Micrometer metrics.

## What This Project Demonstrates

- Java concurrency and correctness-focused algorithm implementation.
- Rate limiting algorithm tradeoffs, not just library usage.
- Redis Lua atomic operations for distributed coordination.
- Graceful degradation when Redis is unavailable.
- Adaptive control with metrics collection and PID adjustment.
- Spring Boot integration through annotation, AOP, configuration binding, and Actuator.
- Extensibility through Java SPI.
- Benchmark discipline through JMH smoke commands and documented caveats.

## Architecture Talking Points

The core interface is `RateLimiter`. Local algorithms, Redis-backed limiters, and adaptive wrappers all fit behind that interface. `RateLimiterFactory` handles keyed local limiter reuse and exposes snapshots for monitoring. Spring AOP is layered on top through `@RateLimit`, so business methods do not need to call limiter APIs manually.

Metrics are intentionally split into two surfaces:

- `/api/ratelimit/stats` returns detailed current-JVM limiter snapshots for the dashboard.
- `/actuator/metrics/ratelimiter.*` exposes aggregate Micrometer gauges for standard Spring Boot monitoring.

## Algorithm Tradeoffs

| Algorithm | Best For | Tradeoff |
|----------|----------|----------|
| Token Bucket | APIs that allow short bursts | Burst size depends on capacity |
| Leaky Bucket | Smooth traffic shaping | Less tolerant of burst traffic |
| Fixed Window | Simple high-throughput counting | Boundary burst is possible |
| Sliding Window | More accurate recent-window limiting | More memory and synchronization cost |

## Distributed Design Talking Points

The Redis limiter uses Lua to perform refill and acquire atomically. That avoids the race conditions that would appear if Java issued separate Redis commands for read, compute, and write.

The fallback strategy is explicit: `DegradingRateLimiter` composes a distributed limiter and a local fallback limiter. When Redis is unhealthy or a command fails, traffic can still be protected locally instead of failing open or blocking all requests.

## Adaptive Limiting Talking Points

The adaptive layer uses a PID controller to adjust QPS based on runtime system metrics. The current design keeps scheduling explicit instead of hiding it behind Spring `@Scheduled`, which makes the control loop easier to test and reuse.

## Observability Talking Points

The project provides three visibility levels:

1. Runtime stats from each limiter through `RateLimiterStats`.
2. JSON snapshots from `/api/ratelimit/stats`.
3. Standard Micrometer gauges through Actuator:
   - `ratelimiter.limiters`
   - `ratelimiter.requests.allowed`
   - `ratelimiter.requests.rejected`
   - `ratelimiter.permits.available`

## Likely Interview Questions

### Why use `synchronized` in some algorithms?

Correctness is the first goal. Token bucket, leaky bucket, and exact sliding window all update multiple pieces of state together. A simple lock makes the invariant clear and testable. If profiling later proves it is a bottleneck, the implementation can be optimized with more complex structures.

### Why not expose per-key Micrometer gauges?

Limiter keys can be unbounded in real systems. Dynamically creating a gauge per arbitrary key can leak meters and hurt monitoring systems. This project exposes aggregate Micrometer gauges and keeps per-key detail in the JSON dashboard endpoint.

### Why benchmark against Guava and Sentinel?

They provide recognizable reference points. The benchmark is not a universal ranking because semantics differ, but it helps compare local call overhead under controlled JMH settings.

### What would you improve next?

The strongest next steps are Testcontainers Redis integration tests, Micrometer meter tags with bounded key policy, a Spring Boot starter package, and production-ready examples.

## Resume Bullet Examples

- Built a Java 17 / Spring Boot 3 distributed rate limiting middleware with token bucket, leaky bucket, fixed window, sliding window, Redis Lua distributed limiting, and local fallback.
- Added adaptive QPS adjustment with system metrics collection and PID control, plus Spring `@RateLimit` AOP integration and Java SPI extension points.
- Implemented observability through REST snapshots, ECharts dashboard, and Actuator/Micrometer aggregate metrics; added JMH benchmark entries for local algorithms, Guava, and Sentinel.
