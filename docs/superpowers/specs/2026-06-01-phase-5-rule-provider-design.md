# Phase 5.2 Rule Provider Design

## Goal

Add configuration-file rule support for the Spring annotation integration.

This phase lets application code keep `@RateLimit(key = "...")` stable while moving limiter settings into `application.yml` or `application.properties`.

## Scope

In scope:

- Bind `ratelimiter.rules` from Spring Boot configuration.
- Represent each configured rule as a typed model.
- Resolve a rule by limiter key.
- Update `RateLimitAspect` so configuration rules take precedence over annotation attributes.
- Keep annotation attributes as fallback when no configured rule exists.
- Test configured rule resolution, annotation fallback, and blank-key default lookup.

Out of scope:

- Java SPI extension points.
- Nacos, Apollo, or other dynamic configuration centers.
- Runtime hot reload.
- Redis distributed mode through annotations.
- Adaptive mode through annotations.
- Custom reject handlers.

## Configuration Format

YAML example:

```yaml
ratelimiter:
  rules:
    order:create:
      algorithm: TOKEN_BUCKET
      capacity: 100
      rate-per-second: 10.0
      window-millis: 1000
      permits: 1
```

Properties example:

```properties
ratelimiter.rules[order:create].algorithm=TOKEN_BUCKET
ratelimiter.rules[order:create].capacity=100
ratelimiter.rules[order:create].rate-per-second=10.0
ratelimiter.rules[order:create].window-millis=1000
ratelimiter.rules[order:create].permits=1
```

## Resolution Rules

The aspect resolves a limiter key exactly as Phase 5.1 does:

- If `@RateLimit.key()` is non-blank, use that key.
- Otherwise use `<fully-qualified-class-name>#<method-name>`.

Then it resolves limiter settings:

1. If a configured rule exists for the resolved key, use the configured rule.
2. If no configured rule exists, use annotation attributes.

This means a method can be configured in either style:

```java
@RateLimit(key = "order:create")
public void createOrder() {
}
```

or:

```java
@RateLimit(capacity = 100, ratePerSecond = 10.0)
public void createOrder() {
}
```

## Components

### `RateLimitRule`

Package: `com.example.ratelimiter.rule`

Immutable rule model containing:

- `AlgorithmType algorithm`
- `long capacity`
- `double ratePerSecond`
- `long windowMillis`
- `int permits`

It should provide:

- A constructor with validation-compatible values.
- `toConfig()` returning a `RateLimiterConfig`.

Validation should rely on `RateLimiterConfig` where possible to avoid duplicating rules.

### `RateLimitProperties`

Package: `com.example.ratelimiter.rule`

Spring Boot configuration properties class:

```java
@ConfigurationProperties(prefix = "ratelimiter")
public class RateLimitProperties {
    private Map<String, RateLimitRule> rules = new HashMap<>();
}
```

It should expose `rules()` as an unmodifiable view or ordinary getter/setter compatible with Spring binding.

### `RateLimitRuleProvider`

Package: `com.example.ratelimiter.rule`

Small lookup component:

- Constructor accepts `RateLimitProperties`.
- `Optional<RateLimitRule> findRule(String key)`.

This is not the Java SPI yet. It is the local Spring configuration provider that a later SPI layer can wrap or replace.

### `RateLimitAspect`

Current behavior:

- Always builds `RateLimiterConfig` directly from annotation attributes.
- Always uses annotation `permits`.

New behavior:

- Resolve key first.
- Ask `RateLimitRuleProvider` for the key.
- If a rule exists, use `rule.toConfig()` and `rule.permits()`.
- If no rule exists, use annotation attributes and annotation `permits`.

For backward compatibility, keep a constructor that accepts only `RateLimiterFactory`; it should use an empty provider. Add a second constructor accepting both `RateLimiterFactory` and `RateLimitRuleProvider`.

## Spring Wiring

Enable configuration properties for `RateLimitProperties`.

The simplest implementation is to annotate `RateLimitProperties` with `@ConfigurationProperties(prefix = "ratelimiter")` and add `@EnableConfigurationProperties(RateLimitProperties.class)` to a small configuration class or to the provider/aspect wiring.

The final implementation should work in a Spring Boot test without requiring application code to manually instantiate `RateLimitProperties`.

## Testing

Tests should cover:

- `RateLimitRule.toConfig()` converts configured values to `RateLimiterConfig`.
- `RateLimitRuleProvider` finds existing keys and returns empty for missing keys.
- `RateLimitAspect` uses configured rules before annotation attributes.
- `RateLimitAspect` falls back to annotation attributes when a key has no configured rule.
- Blank annotation key can still use the default class/method key to find a configured rule.

Use `ratePerSecond = 0.0` and small capacities to avoid timing-sensitive sleeps.

## Documentation

Update README with:

- `application.yml` example.
- `@RateLimit(key = "...")` example.
- Clear precedence rule: configuration by key overrides annotation attributes; annotation attributes are fallback.
- Current limitations: no SPI, no dynamic refresh, no distributed/adaptive annotation mode.

## Success Criteria

- Existing Phase 5.1 annotation behavior remains compatible.
- Configured rules can limit a method even when annotation capacity would allow more calls.
- Missing configured rules do not break existing annotation-only usage.
- Full test suite passes.
- Benchmark jar still builds.
- JMH smoke benchmark still runs.
