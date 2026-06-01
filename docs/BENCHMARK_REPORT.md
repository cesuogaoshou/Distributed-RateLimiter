# Benchmark Report

This project uses JMH for benchmark execution. The benchmark entries are intended to provide reproducible local measurements and comparison points, not fixed universal performance claims.

## Environment Used During Development

Observed smoke runs during development used:

- Windows PowerShell
- Java 21.0.6 runtime
- Project compiled with Java 17 release settings
- JMH 1.37
- One fork, one warmup iteration, one measurement iteration for smoke checks

These smoke settings are intentionally short. They verify that the benchmark jar works, but they are not enough for final performance claims.

## Build Command

```powershell
mvn -Pbenchmark -DskipTests package
```

## Local Limiter Smoke Command

```powershell
java -jar target/benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
```

Representative smoke results observed during development:

| Work stage | Benchmark | Smoke score |
|-----------|-----------|-------------|
| Benchmarking phase | `LocalRateLimiterBenchmark.tokenBucketSingleThread` | 23,302,459 ops/s |
| Redis phase | `LocalRateLimiterBenchmark.tokenBucketSingleThread` | 23,569,470 ops/s |
| Adaptive phase | `LocalRateLimiterBenchmark.tokenBucketSingleThread` | 23,474,001 ops/s |
| Rule provider SPI phase | `LocalRateLimiterBenchmark.tokenBucketSingleThread` | 25,113,755 ops/s |
| Algorithm SPI phase | `LocalRateLimiterBenchmark.tokenBucketSingleThread` | 23,098,251 ops/s |

These numbers are smoke readings from the development machine. They should be re-run on the target machine before being used in a README table, blog post, or interview claim.

## Comparison Benchmark Command

```powershell
java -jar target/benchmarks.jar ComparisonRateLimiterBenchmark -wi 1 -i 1 -f 1
```

Sentinel-only smoke command:

```powershell
java -jar target/benchmarks.jar ComparisonRateLimiterBenchmark.sentinel -wi 1 -i 1 -f 1
```

## How To Produce Stronger Results

For more reliable benchmark reporting, run with longer warmups and multiple forks:

```powershell
java -jar target/benchmarks.jar LocalRateLimiterBenchmark -wi 5 -i 5 -f 3
java -jar target/benchmarks.jar ComparisonRateLimiterBenchmark -wi 5 -i 5 -f 3
```

Record:

- CPU model
- core count
- operating system
- JDK version
- exact command
- benchmark output
- whether the machine was plugged in and idle

## Interpretation Notes

- Token Bucket, Guava `RateLimiter`, and Sentinel do not have identical semantics.
- Sentinel measures resource entry/exit behavior, not only a token bucket check.
- Very short JMH smoke runs can be noisy.
- Throughput alone does not prove correctness under concurrent edge cases.
- README should only publish benchmark conclusions when the raw command output is kept with the result.
