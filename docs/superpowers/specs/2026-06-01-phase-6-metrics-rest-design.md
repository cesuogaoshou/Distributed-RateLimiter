# Phase 6.3 Metrics REST API Design

## Goal

Expose current in-process rate limiter statistics through a small REST API that can later feed the dashboard.

## Scope

- Add Spring MVC support through `spring-boot-starter-web`.
- Let `RateLimiterFactory` expose immutable snapshots of all limiters it has created.
- Add `RateLimiterMetricsSnapshot` as the JSON-facing data model.
- Add `RateLimiterMetricsService` to collect factory snapshots.
- Add `RateLimiterMetricsController` with `GET /api/ratelimit/stats`.
- Document local run and curl/browser access in README.

## Non-Goals

- Do not implement Dashboard UI in this phase.
- Do not add SSE streaming in this phase.
- Do not add Micrometer meter registration in this phase.
- Do not aggregate stats across multiple JVM instances.
- Do not query Redis for cluster-wide counters.

## API

`GET /api/ratelimit/stats`

Response:

```json
[
  {
    "key": "order:create",
    "allowedRequests": 10,
    "rejectedRequests": 2,
    "availablePermits": 88
  }
]
```

If no limiter has been created through `RateLimiterFactory`, the endpoint returns an empty array.

## Design Notes

`RateLimiterFactory` already owns the in-memory registry keyed by limiter key. The least invasive design is to expose a read-only snapshot method that iterates through that registry and reads `RateLimiter.getStats()`.

The REST controller should depend on a service rather than directly on the factory. That keeps the controller thin and gives the future dashboard or SSE stream a stable service to reuse.

## Verification

- Unit test `RateLimiterFactory.snapshotStats()`.
- Unit test `RateLimiterMetricsService`.
- MockMvc test for `GET /api/ratelimit/stats`.
- Full `mvn test`.
