# Phase 6.4 Dashboard Design

## Goal

Add a local static dashboard page that visualizes the metrics returned by `GET /api/ratelimit/stats`.

## Chosen Layout

Option A: operations dashboard.

- Top: summary metrics for total allowed requests, total rejected requests, active limiter count, and total available permits.
- Middle: ECharts bar charts for allowed/rejected counts and available permits by limiter key.
- Bottom: limiter detail table with key, allowed, rejected, available permits, and reject ratio.

## Scope

- Add `src/main/resources/static/dashboard.html`.
- Use browser-native JavaScript and ECharts from CDN.
- Fetch `/api/ratelimit/stats`.
- Support manual refresh and periodic refresh.
- Show empty and error states.
- Add a Spring MockMvc test that verifies the static dashboard is served.
- Document `/dashboard.html` in README.

## Non-Goals

- Do not add a frontend build tool.
- Do not add authentication.
- Do not add SSE streaming.
- Do not add backend sample data generation.
- Do not commit temporary `.superpowers` brainstorming files.

## Data Flow

```text
Browser dashboard.html
  -> fetch('/api/ratelimit/stats')
  -> RateLimiterMetricsController
  -> RateLimiterMetricsService
  -> RateLimiterFactory.snapshotStats()
```

## Verification

- `mvn -f .worktrees\dashboard\pom.xml test -Dtest=DashboardStaticResourceTest`
- `mvn -f .worktrees\dashboard\pom.xml test`
- Optional manual check after startup:
  - `mvn spring-boot:run`
  - open `http://localhost:8080/dashboard.html`
