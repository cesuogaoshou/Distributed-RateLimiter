# Quick Start

This guide assumes PowerShell on Windows, Java 17+, and Maven 3.9+.

## 1. Verify Tooling

```powershell
java -version
mvn -version
```

The project is configured for Java 17 source compatibility. Running with a newer JDK is fine as long as Maven can compile with release 17.

## 2. Run Tests

```powershell
mvn test
```

Expected result:

```text
BUILD SUCCESS
Tests run: 96, Failures: 0, Errors: 0, Skipped: 0
```

The exact total may increase as more tests are added, but failures and errors should remain zero.

## 3. Start the Application

```powershell
mvn spring-boot:run
```

The app starts on the default Spring Boot port:

```text
http://localhost:8080
```

## 4. Open Dashboard

Open:

```text
http://localhost:8080/dashboard.html
```

If no limiter has been created through `RateLimiterFactory`, the dashboard shows an empty state. After factory-created limiters process requests, the dashboard shows summary metrics, charts, and a table.

## 5. Read Metrics API

```powershell
curl http://localhost:8080/api/ratelimit/stats
```

Example shape:

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

The endpoint reports the current JVM only.

## 6. Read Actuator Metrics

```powershell
curl http://localhost:8080/actuator/metrics/ratelimiter.limiters
curl http://localhost:8080/actuator/metrics/ratelimiter.requests.allowed
curl http://localhost:8080/actuator/metrics/ratelimiter.requests.rejected
curl http://localhost:8080/actuator/metrics/ratelimiter.permits.available
```

These are aggregate Micrometer gauges for the current JVM. Per-key details remain in `/api/ratelimit/stats` and the dashboard.

## 7. Build Benchmark Jar

```powershell
mvn -Pbenchmark -DskipTests package
```

This creates:

```text
target/benchmarks.jar
```

## 8. Run JMH Smoke Benchmarks

Local token bucket smoke test:

```powershell
java -jar target/benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
```

Guava/Sentinel comparison smoke test:

```powershell
java -jar target/benchmarks.jar ComparisonRateLimiterBenchmark -wi 1 -i 1 -f 1
```

Sentinel-only smoke test:

```powershell
java -jar target/benchmarks.jar ComparisonRateLimiterBenchmark.sentinel -wi 1 -i 1 -f 1
```

Use longer warmup and measurement settings before making any performance claim.

## 9. Redis Limiter Notes

Redis is not required for normal tests. To use `RedisRateLimiter`, provide a `RedisCommandExecutor`, usually through `SpringDataRedisCommandExecutor` with a configured `StringRedisTemplate`.

The distributed limiter does not currently have an automatic Spring Boot starter. Wire it explicitly where needed.

## Troubleshooting

### Maven Cannot Download Dependencies

If Maven cannot connect to Maven Central, check proxy settings and retry. In this environment, GitHub access has previously required a scoped proxy for Git only; Maven may need a separate network/proxy setup.

### Dashboard Is Empty

This is expected before any limiter is created through `RateLimiterFactory`. The dashboard visualizes runtime state, not static configuration.

### Benchmark Jar Missing

Run the benchmark profile build first:

```powershell
mvn -Pbenchmark -DskipTests package
```
