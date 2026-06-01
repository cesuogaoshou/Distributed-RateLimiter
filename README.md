# Distributed RateLimiter

一个用 Java 和 Spring Boot 实现的限流中间件练习项目，包含本地限流、Redis 分布式限流、自适应限流、注解接入、扩展点、监控接口和本地 Dashboard。

## Code Example

```java
RateLimiterConfig config = RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
        .capacity(100)
        .ratePerSecond(10.0)
        .window(Duration.ofSeconds(1))
        .build();

RateLimiter limiter = new TokenBucketRateLimiter(config);

if (limiter.tryAcquire()) {
    // handle request
}
```

Spring 方法也可以用注解接入：

```java
@RateLimit(
        key = "order:create",
        algorithm = AlgorithmType.TOKEN_BUCKET,
        capacity = 100,
        ratePerSecond = 10.0,
        windowMillis = 1000
)
public void createOrder() {
    // business logic
}
```

## Architecture

项目核心是一组统一的 `RateLimiter` 实现。调用方只依赖 `tryAcquire()` 这类基础接口，具体限流算法由 `RateLimiterConfig` 和工厂类决定。

本地算法层实现了令牌桶、漏桶、固定窗口和滑动窗口。它们都维护自己的请求统计，供监控接口和 Dashboard 使用。

分布式限流层通过 Redis Lua 脚本实现原子化的令牌桶操作。Lua 脚本把补充令牌、扣减令牌和写回状态放在一次 Redis 执行里，避免多实例并发时出现超发。`DegradingRateLimiter` 提供降级能力，当 Redis 不可用时可以回退到本地限流器。

Spring 接入层通过 `@RateLimit` 和 AOP 在业务方法执行前做限流判断。规则可以来自注解，也可以来自配置和 SPI 扩展。SPI 部分提供了拒绝处理器、规则提供器和算法注册扩展点。

监控层暴露当前 JVM 内注册限流器的统计数据，并提供一个本地 Dashboard 页面查看允许请求、拒绝请求、可用令牌和拒绝率。

## Run

环境要求：

- Java 17+
- Maven 3.9+
- Redis 只在手动使用 Redis 分布式限流时需要

运行测试：

```powershell
mvn test
```

启动应用：

```powershell
mvn spring-boot:run
```

打开 Dashboard：

```text
http://localhost:8080/dashboard.html
```

生成本地演示流量：

```powershell
curl.exe http://localhost:8080/demo/orders
```

连续请求可以看到拒绝统计变化：

```powershell
1..10 | ForEach-Object { curl.exe http://localhost:8080/demo/orders }
```

构建并运行一个 JMH smoke benchmark：

```powershell
mvn -Pbenchmark -DskipTests package
java -jar target/benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
```

## What I Learned

这个项目主要用于把零散的 Java 后端知识串成一个完整工程。我通过它练习了限流算法的实现方式、并发状态维护、Redis Lua 原子操作、Spring AOP 接入、Java SPI 扩展、JMH 基准测试、Micrometer/Actuator 指标暴露，以及如何把一个功能模块整理成可运行、可测试、可观察的项目。
