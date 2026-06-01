# Phase 5.3 RejectHandler SPI Design

## Goal

Add the first Java SPI extension point to the project by making rate-limit rejection handling pluggable.

The default behavior remains unchanged: rejected calls throw `RateLimitException`.

## Scope

In scope:

- Define a `RejectHandler` SPI.
- Provide a default reject handler that throws `RateLimitException`.
- Load external reject handlers with Java `ServiceLoader`.
- Choose the handler with the highest priority.
- Update `RateLimitAspect` to delegate rejection to the loaded handler.
- Keep existing AOP behavior and tests compatible.

Out of scope:

- `RuleProvider` SPI.
- `RateLimiterAlgorithm` SPI.
- Handler selection by annotation.
- Multiple handler chain execution.
- External sample jar project.
- Dynamic reload of SPI providers.

## Public SPI

Package: `com.example.ratelimiter.spi`

```java
public interface RejectHandler {

    void handle(String key, RateLimit rateLimit);

    default int priority() {
        return 0;
    }
}
```

Contract:

- `key` is the resolved limiter key.
- `rateLimit` is the annotation on the rejected method.
- Implementations may throw any runtime exception.
- If an implementation returns normally, the aspect should still treat the method invocation as rejected and should not call the business method.

For this phase, the default handler throws `RateLimitException`.

## Components

### `DefaultRejectHandler`

Package: `com.example.ratelimiter.spi`

Implements `RejectHandler`.

Behavior:

- Throws `RateLimitException("Rate limit exceeded for key: " + key)`.
- Uses priority `0`.

### `RejectHandlerLoader`

Package: `com.example.ratelimiter.spi`

Responsibilities:

- Load `RejectHandler` implementations through `ServiceLoader.load(RejectHandler.class)`.
- Select the implementation with the highest `priority()`.
- Fall back to `DefaultRejectHandler` when no provider exists.

The loader should expose:

```java
public RejectHandler load()
```

### `RateLimitAspect`

Current behavior:

- On rejection, directly throws `RateLimitException`.

New behavior:

- Keep current constructors compatible.
- Store a `RejectHandler`.
- Default production constructor path uses `new RejectHandlerLoader().load()`.
- Tests can pass a specific `RejectHandler`.
- On rejection, call `rejectHandler.handle(key, rateLimit)` and do not proceed with the join point.
- If `handle` returns normally, return `null` from the aspect. This is acceptable for this phase because the default handler throws, and custom handlers that return normally are taking responsibility for the result.

## Service Registration

No in-repo `META-INF/services` provider is required for this phase because the default handler is fallback behavior, not a ServiceLoader provider.

External users can register a custom handler by adding a file:

```text
META-INF/services/com.example.ratelimiter.spi.RejectHandler
```

with one implementation class name per line.

## Testing

Tests should cover:

- `DefaultRejectHandler` throws `RateLimitException`.
- `RejectHandlerLoader` returns the default handler when no service provider is available.
- `RejectHandlerLoader` chooses the highest-priority handler when provided a collection in a test-friendly constructor or helper.
- `RateLimitAspect` still throws `RateLimitException` through the default handler.
- `RateLimitAspect` delegates rejection to a custom handler in a focused unit test.

The loader should remain easy to test without creating an external jar. A package-private or public constructor accepting `Iterable<RejectHandler>` is acceptable for tests.

## Documentation

Update README:

- Add a short SPI section.
- Show a custom `RejectHandler` implementation.
- Show the `META-INF/services/com.example.ratelimiter.spi.RejectHandler` registration file path.
- State that only rejection handling is pluggable in this phase; rule provider and algorithm SPI are planned later.

## Success Criteria

- Existing `@RateLimit` behavior remains unchanged by default.
- Full test suite passes.
- Benchmark jar still builds.
- JMH smoke benchmark still runs.
- README documents how to implement and register a custom reject handler.
