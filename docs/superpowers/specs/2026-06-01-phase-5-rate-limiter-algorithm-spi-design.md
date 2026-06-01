# Phase 5.5 RateLimiterAlgorithm SPI Design

## Goal

Add a Java SPI extension point for custom rate limiter algorithms and wire it into `RateLimiterFactory`.

Built-in algorithms must keep their current behavior and API. External algorithms should be registered by string name instead of trying to extend the `AlgorithmType` enum.

## Scope

In scope:

- Define a public `RateLimiterAlgorithm` SPI.
- Load external algorithm providers with Java `ServiceLoader`.
- Select the highest-priority provider for duplicate names.
- Add a custom algorithm name path to `RateLimiterConfig`.
- Update `RateLimiterFactory` to create custom algorithm limiters through the SPI registry.
- Keep built-in `AlgorithmType` creation unchanged.

Out of scope:

- External sample jar project.
- Dynamic reload of algorithm providers.
- Letting SPI providers override built-in `AlgorithmType` values.
- Redis distributed limiter factory wiring.
- Dashboard or metrics changes.
- Combining multiple providers for one algorithm name.

## Why Custom Algorithms Use String Names

`AlgorithmType` is a Java enum. External jars cannot add enum constants to this project at runtime.

For that reason, custom algorithms should register a string name such as `custom-token-bucket` or `warmup-bucket`. The built-in enum remains the stable path for built-in algorithms, while custom names provide an extension path for SPI implementations.

## Public SPI

Package: `com.example.ratelimiter.spi`

```java
public interface RateLimiterAlgorithm {

    String name();

    RateLimiter create(RateLimiterConfig config);

    default int priority() {
        return 0;
    }
}
```

Contract:

- `name()` is the custom algorithm id used by `RateLimiterConfig`.
- Names should be non-blank.
- `create(config)` returns a new `RateLimiter` instance.
- Higher `priority()` wins if multiple providers use the same name.
- Providers cannot override built-in `AlgorithmType` creation in this phase.

## `RateLimiterConfig` Changes

Current record fields:

```java
AlgorithmType algorithm
long capacity
double ratePerSecond
Duration window
```

New record fields:

```java
AlgorithmType algorithm
String customAlgorithm
long capacity
double ratePerSecond
Duration window
```

Compatibility rules:

- Existing `RateLimiterConfig.builder(AlgorithmType algorithm)` remains and sets `customAlgorithm` to `null`.
- Add `RateLimiterConfig.customAlgorithm(String customAlgorithm)` returning a builder for SPI algorithms.
- Built-in configs require a non-null `algorithm` and `customAlgorithm == null`.
- Custom configs require `algorithm == null` and a non-blank `customAlgorithm`.
- `toBuilder()` preserves whichever path was used.
- Existing code using `config.algorithm()` keeps compiling because the accessor remains.
- New code can use `config.customAlgorithm()`.

Invalid configs should throw `RateLimiterConfigException` with clear messages.

## Components

### `RateLimiterAlgorithmRegistry`

Package: `com.example.ratelimiter.spi`

Responsibilities:

- Store providers by `name()`.
- For duplicate names, keep the provider with the highest `priority()`.
- Expose:

```java
public Optional<RateLimiterAlgorithm> find(String name)
```

The registry should be immutable from callers' perspective after construction.

### `RateLimiterAlgorithmLoader`

Package: `com.example.ratelimiter.spi`

Responsibilities:

- Load `RateLimiterAlgorithm` implementations through `ServiceLoader.load(RateLimiterAlgorithm.class)`.
- Build a `RateLimiterAlgorithmRegistry`.
- Provide a test-friendly constructor accepting `Iterable<RateLimiterAlgorithm>`.

The loader should expose:

```java
public RateLimiterAlgorithmRegistry load()
```

## `RateLimiterFactory` Integration

Current behavior:

- Uses `switch (config.algorithm())`.
- Creates built-in local limiters.
- Rejects `DISTRIBUTED_TOKEN_BUCKET` because it requires an explicit Redis executor.

New behavior:

1. If `config.customAlgorithm()` is non-null, resolve it from `RateLimiterAlgorithmRegistry`.
2. If found, call `algorithm.create(config)`.
3. If not found, throw `IllegalArgumentException("unknown custom rate limiter algorithm: " + name)`.
4. If `customAlgorithm` is null, keep the current built-in `AlgorithmType` switch unchanged.

Constructors:

- Keep the no-arg constructor:

```java
public RateLimiterFactory()
```

It should load the default SPI registry with `new RateLimiterAlgorithmLoader().load()`.

- Add a constructor for tests/manual wiring:

```java
public RateLimiterFactory(RateLimiterAlgorithmRegistry algorithmRegistry)
```

Because `RateLimiterFactory` is a Spring component, there must not be ambiguous Spring constructor injection. If needed, annotate the no-arg constructor with `@Autowired` or keep only one constructor visible to Spring and make the registry constructor package-private or public without Spring annotation. The implementation plan should verify `RateLimiterApplicationTests.contextLoads`.

## Service Registration

External users can register a custom algorithm by adding:

```text
META-INF/services/com.example.ratelimiter.spi.RateLimiterAlgorithm
```

with one implementation class name per line.

No in-repo `META-INF/services` provider is required for this phase.

## Testing

Tests should cover:

- `RateLimiterConfig.builder(AlgorithmType)` still builds built-in configs.
- `RateLimiterConfig.customAlgorithm(String)` builds custom configs.
- Invalid custom algorithm names are rejected.
- Built-in configs still reject null enum values.
- `RateLimiterAlgorithmRegistry` selects the highest-priority provider for duplicate names.
- `RateLimiterAlgorithmLoader` returns an empty registry when no providers exist.
- `RateLimiterFactory` creates built-in limiters as before.
- `RateLimiterFactory` creates a custom limiter from an injected registry.
- `RateLimiterFactory` rejects unknown custom algorithm names.
- `RateLimiterApplicationTests.contextLoads` passes after constructor changes.

## Documentation

Update README:

- Current phase becomes `Phase 5.5: RateLimiterAlgorithm SPI 扩展点`.
- Completed list includes Java SPI `RateLimiterAlgorithm`.
- SPI section documents `RejectHandler`, `RuleProvider`, and `RateLimiterAlgorithm`.
- Show custom algorithm implementation and service registration file path.
- State that custom algorithms use string names and do not override built-in enum algorithms.
- Update current limitations to remove `RateLimiterAlgorithm SPI` and keep dynamic refresh, distributed annotation mode, and adaptive annotation mode as limitations.

## Success Criteria

- Existing built-in algorithm tests still pass.
- Custom algorithm SPI can create a limiter through `RateLimiterFactory`.
- Unknown custom algorithm names fail with a clear exception.
- Full test suite passes.
- Benchmark jar still builds.
- JMH smoke benchmark still runs.
- README documents how to implement and register a custom algorithm provider.
