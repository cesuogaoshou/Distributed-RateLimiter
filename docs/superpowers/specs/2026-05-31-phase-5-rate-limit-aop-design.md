# Phase 5.1 RateLimit AOP Design

## Goal

Add the first Spring integration layer for the rate limiter middleware: a method-level `@RateLimit` annotation backed by a Spring AOP aspect.

This phase proves that application code can be protected by the existing limiter engine without manually creating limiters in business methods.

## Scope

In scope:

- Method-level `@RateLimit` annotation.
- Spring AOP interception for annotated methods.
- Conversion from annotation attributes to `RateLimiterConfig`.
- Reuse of the existing `RateLimiterFactory`.
- Fast-fail behavior through a `RateLimitException`.
- Integration tests for allowed, rejected, and unannotated method calls.

Out of scope:

- YAML/properties rule loading.
- Java SPI extension points.
- Distributed Redis mode through annotations.
- Adaptive mode through annotations.
- Queueing or blocking wait mode.
- Custom reject handlers.

## Public API

### `@RateLimit`

Package: `com.example.ratelimiter.annotation`

The annotation is applied to methods:

```java
@RateLimit(
        key = "createOrder",
        algorithm = AlgorithmType.TOKEN_BUCKET,
        capacity = 100,
        ratePerSecond = 10.0,
        windowMillis = 1000,
        permits = 1
)
```

Attributes:

- `key`: limiter key. Empty string means the aspect resolves a key from the declaring class and method name.
- `algorithm`: local algorithm type. Defaults to `TOKEN_BUCKET`.
- `capacity`: limiter capacity. Defaults to `100`.
- `ratePerSecond`: refill or processing rate. Defaults to `10.0`.
- `windowMillis`: fixed/sliding window duration. Defaults to `1000`.
- `permits`: permits consumed per invocation. Defaults to `1`.

Only local algorithms are supported in this phase. If the annotation uses `DISTRIBUTED_TOKEN_BUCKET`, the existing factory error behavior is acceptable.

## Components

### `RateLimitException`

Package: `com.example.ratelimiter.exception`

Runtime exception thrown when the aspect rejects a method invocation.

It should include the limiter key in the message so failed calls are debuggable.

### `RateLimitAspect`

Package: `com.example.ratelimiter.aop`

Responsibilities:

- Intercept methods annotated with `@RateLimit`.
- Resolve limiter key.
- Build `RateLimiterConfig` from annotation attributes.
- Get or create a limiter from `RateLimiterFactory`.
- Call `tryAcquire(permits)`.
- Proceed with the method only if acquire succeeds.
- Throw `RateLimitException` when acquire fails.

The aspect should have a constructor accepting `RateLimiterFactory` so tests can wire it directly.

Key resolution:

- If `@RateLimit.key()` is non-blank, use it.
- Otherwise use `<fully-qualified-class-name>#<method-name>`.

## Spring Wiring

Add `spring-boot-starter-aop` to the Maven dependencies.

The aspect should be a Spring component. `RateLimiterFactory` is currently a plain class, so this phase should make it a Spring component as well while keeping direct construction in existing tests valid.

## Testing

Create a Spring Boot test for the aspect with an inner test service.

Test cases:

- Annotated method allows calls within capacity.
- Annotated method throws `RateLimitException` after permits are exhausted.
- Unannotated method is not limited.
- Blank annotation key uses the default class/method key and still limits.

The test should avoid timing-sensitive sleeps. Use capacity-limited token bucket settings with `ratePerSecond = 0.0` so permits do not refill during the test.

## Documentation

Update README with a short annotation example and state the current limits:

- Local mode only.
- Fast-fail only.
- YAML, SPI, distributed, and adaptive annotation modes are planned later.

## Success Criteria

- Existing local, distributed, adaptive, and benchmark code still compiles.
- New AOP tests pass.
- Full test suite passes.
- Benchmark jar still builds.
- JMH smoke benchmark still runs.
