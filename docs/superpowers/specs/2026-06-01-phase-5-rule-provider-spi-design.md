# Phase 5.4 RuleProvider SPI Design

## Goal

Add a Java SPI extension point for external rule providers and wire it into the existing `@RateLimit` rule resolution path.

The current YAML/properties rule behavior must remain compatible when no SPI provider exists.

## Scope

In scope:

- Define a public `RuleProvider` SPI.
- Load external providers with Java `ServiceLoader`.
- Select the provider with the highest `priority()`.
- Add an empty fallback provider when no SPI provider exists.
- Update `RateLimitAspect` so SPI rules are checked before YAML/properties rules.
- Keep annotation parameters as the final fallback.

Out of scope:

- Combining multiple providers.
- Provider chain execution.
- Dynamic reload of SPI providers.
- Nacos, Apollo, database, or remote config implementations.
- `RateLimiterAlgorithm` SPI.
- Changes to the existing YAML/properties binding model.

## Public SPI

Package: `com.example.ratelimiter.spi`

```java
public interface RuleProvider {

    Optional<RateLimitRule> findRule(String key);

    default int priority() {
        return 0;
    }
}
```

Contract:

- `key` is the resolved limiter key used by `RateLimitAspect`.
- Returning `Optional.empty()` means this provider has no rule for the key.
- Implementations should return fully populated `RateLimitRule` objects.
- Higher `priority()` wins when multiple providers are discovered.
- This phase selects one provider only; it does not merge or cascade multiple providers.

## Components

### `EmptyRuleProvider`

Package: `com.example.ratelimiter.spi`

Implements `RuleProvider`.

Behavior:

- Always returns `Optional.empty()`.
- Uses priority `0`.

This is the fallback when `ServiceLoader` discovers no provider.

### `RuleProviderLoader`

Package: `com.example.ratelimiter.spi`

Responsibilities:

- Load `RuleProvider` implementations through `ServiceLoader.load(RuleProvider.class)`.
- Select the implementation with the highest `priority()`.
- Fall back to `EmptyRuleProvider` when no provider exists.

The loader should expose:

```java
public RuleProvider load()
```

For tests, a constructor accepting `Iterable<RuleProvider>` is acceptable and should mirror the `RejectHandlerLoader` pattern.

## Rule Resolution Order

The `@RateLimit` resolution chain becomes:

1. SPI `RuleProvider`
2. Existing YAML/properties `RateLimitRuleProvider`
3. Annotation attributes

Rationale:

- SPI is an explicit extension point and should be able to override static local configuration.
- YAML/properties remains the built-in application configuration path.
- Annotation attributes preserve backward compatibility and keep simple usage working.

## `RateLimitAspect` Integration

Current fields:

- `RateLimiterFactory`
- `RateLimitRuleProvider`
- `RejectHandler`

New field:

- `RuleProvider spiRuleProvider`

Constructor rules:

- Keep the one-argument constructor for backward-compatible manual construction.
- Keep exactly one `@Autowired` constructor for Spring:

```java
RateLimitAspect(RateLimiterFactory rateLimiterFactory, RateLimitRuleProvider ruleProvider)
```

- The Spring constructor loads defaults:

```java
new RejectHandlerLoader().load()
new RuleProviderLoader().load()
```

- Add a four-argument constructor for tests and manual wiring:

```java
RateLimitAspect(
        RateLimiterFactory rateLimiterFactory,
        RateLimitRuleProvider ruleProvider,
        RejectHandler rejectHandler,
        RuleProvider spiRuleProvider)
```

Do not annotate the four-argument constructor with `@Autowired`; previous phases showed that ambiguous constructors can break Spring application context loading.

The rule resolver should be equivalent to:

```java
private ResolvedRule resolveRule(String key, RateLimit rateLimit) {
    return spiRuleProvider.findRule(key)
            .or(() -> ruleProvider.findRule(key))
            .map(rule -> new ResolvedRule(rule.toConfig(), rule.getPermits()))
            .orElseGet(() -> new ResolvedRule(toConfig(rateLimit), rateLimit.permits()));
}
```

## Service Registration

External users can register a custom provider by adding:

```text
META-INF/services/com.example.ratelimiter.spi.RuleProvider
```

with one implementation class name per line.

No in-repo `META-INF/services` provider is required for this phase because fallback behavior is handled by `EmptyRuleProvider`.

## Testing

Tests should cover:

- `EmptyRuleProvider` always returns empty.
- `RuleProviderLoader` returns `EmptyRuleProvider` when no provider exists.
- `RuleProviderLoader` chooses the highest-priority provider when given multiple providers in a test-friendly constructor.
- `RateLimitAspect` uses an SPI rule before a YAML/properties rule for the same key.
- `RateLimitAspect` falls back to YAML/properties when the SPI provider returns empty.
- `RateLimitAspect` falls back to annotation attributes when both SPI and YAML/properties miss.
- Existing AOP rejection behavior through `RejectHandler` remains compatible.

## Documentation

Update README:

- Current phase becomes `Phase 5.4: RuleProvider SPI 扩展点`.
- Completed list includes Java SPI `RuleProvider`.
- SPI section documents both `RejectHandler` and `RuleProvider`.
- Show custom provider implementation and service registration file path.
- State that this phase selects one highest-priority provider and does not merge multiple providers.
- Keep `RateLimiterAlgorithm` SPI listed as planned.

## Success Criteria

- Existing YAML/properties behavior remains unchanged when no SPI provider exists.
- SPI provider can override YAML/properties rules for the same key.
- Full test suite passes.
- Benchmark jar still builds.
- JMH smoke benchmark still runs.
- README documents how to implement and register a custom rule provider.
