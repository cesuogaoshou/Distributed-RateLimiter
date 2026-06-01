# Phase 8 Resume Polish Design

## Goal

Raise the project from "feature-complete demo" to "resume-ready infrastructure project" by adding a standard observability integration and focused interview/project-showcase documentation.

## Scope

This phase adds:

- Spring Boot Actuator dependency and metrics endpoint exposure.
- Micrometer aggregate gauges for current JVM limiter state.
- Documentation for interview talking points and benchmark interpretation.
- README updates that present these additions clearly.

This phase does not add a Spring Boot starter module, Redis Testcontainers, or a full benchmark result suite.

## Metrics Design

Add a `MeterBinder` component that reads `RateLimiterMetricsService.snapshots()` and registers aggregate gauges:

- `ratelimiter.limiters`: number of factory-created limiters in the current JVM.
- `ratelimiter.requests.allowed`: sum of allowed requests across current JVM limiters.
- `ratelimiter.requests.rejected`: sum of rejected requests across current JVM limiters.
- `ratelimiter.permits.available`: sum of available permits across current JVM limiters.

The binder intentionally exports aggregate gauges instead of per-key gauges. Per-key metrics are better handled with careful tag lifecycle management; adding dynamic gauges for arbitrary limiter keys can leak meters when keys are unbounded.

## Endpoint Design

Add `spring-boot-starter-actuator` and expose:

```properties
management.endpoints.web.exposure.include=health,metrics
```

This makes `/actuator/metrics/ratelimiter.limiters` and the other metric names available when the application is running.

## Documentation Design

Add:

- `docs/INTERVIEW_NOTES.md`: project pitch, technical highlights, tradeoffs, likely interview questions.
- `docs/BENCHMARK_REPORT.md`: benchmark commands, smoke results already observed during development, and warnings about interpreting JMH data.

Update README with:

- Feature matrix row for Actuator/Micrometer.
- Monitoring section commands for Actuator metrics.
- Documentation links.
- A resume-friendly project summary.

## Verification

- Unit test the `MeterBinder` with `SimpleMeterRegistry`.
- Integration test that Actuator exposes `ratelimiter.limiters`.
- Run full `mvn test`.
