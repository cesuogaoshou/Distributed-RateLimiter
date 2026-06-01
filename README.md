# Distributed RateLimiter

高性能分布式限流中间件项目，目标是系统性实现单机限流、分布式限流、自适应限流、性能基准测试、监控面板和 Spring Boot 接入层。

当前仓库处于 Phase 5.2 YAML/properties 规则提供阶段，后续工作以 [PROJECT_OUTLINE.md](PROJECT_OUTLINE.md) 为主路线图，以 [Distributed-RateLimiter-Spec.md](Distributed-RateLimiter-Spec.md) 为完整规格参考。

## 目标技术栈

- Java 17
- Spring Boot 3
- Maven
- JUnit 5
- Redis + Lua
- JMH
- Micrometer
- HTML + ECharts

## 当前阶段

Phase 5.2: YAML/properties 规则提供。

已完成：

- 项目规格文档
- 项目大纲
- Git 忽略规则
- 仓库基础 README
- Maven/Spring Boot 3 工程
- 限流核心接口、配置模型和统计模型
- 令牌桶、漏桶、固定窗口、滑动窗口四种单机算法
- 单元测试和并发测试
- JMH benchmark profile
- 四种单机算法的 JMH 基准测试
- Redis Lua 分布式令牌桶
- Redis 健康检查和本地降级策略
- 自适应限流指标模型、PID 控制器、调度器和本地限流器适配层
- Spring `@RateLimit` 注解和 AOP 快速失败接入
- YAML/properties 限流规则绑定和配置优先解析

下一步：

- SPI 扩展点
- 补充 Guava/Sentinel 对比入口

## 开发原则

- 正确性优先于性能。
- 每种算法都必须有单元测试和并发测试。
- 性能数据必须来自可复现的 JMH 结果。
- README 和测试是项目交付的一部分。

## 本地开发

运行测试：

```powershell
mvn test
```

构建项目：

```powershell
mvn package
```

## 单机限流示例

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

## 单机算法

| 算法 | 特点 | 当前实现 |
|------|------|----------|
| Token Bucket | 允许短时突发，按固定速率补充令牌 | `synchronized` 保证 refill + acquire 原子性 |
| Leaky Bucket | 平滑流量，桶满拒绝 | `synchronized` 保证 drain + acquire 原子性 |
| Fixed Window | 实现简单，窗口边界可能突刺 | `AtomicLong` + 窗口刷新 |
| Sliding Window | 更精确地限制最近时间窗口 | `synchronized` + 时间戳队列 |

## JMH 基准测试

构建 benchmark jar：

```powershell
mvn -Pbenchmark -DskipTests package
```

运行全部本地限流 benchmark：

```powershell
java -jar target/benchmarks.jar LocalRateLimiterBenchmark
```

快速 smoke test：

```powershell
java -jar target/benchmarks.jar LocalRateLimiterBenchmark.tokenBucketSingleThread -wi 1 -i 1 -f 1
```

当前 benchmark 覆盖：

| 算法 | 线程维度 |
|------|----------|
| Token Bucket | 1 / 4 / 8 |
| Leaky Bucket | 1 / 4 / 8 |
| Fixed Window | 1 / 4 / 8 |
| Sliding Window | 1 / 4 / 8 |

README 中的性能数据必须来自本机实际运行结果，不写虚构数据。后续与 Guava/Sentinel 的对比会单独扩展。

## 分布式限流

Phase 3 引入了基于 Redis Lua 的分布式令牌桶：

- Redis Lua 保证 refill + acquire 的原子性。
- `RedisRateLimiter` 实现和单机限流器相同的 `RateLimiter` 接口。
- `DegradingRateLimiter` 在 Redis 不可用或命令失败时降级到本地令牌桶。
- `RedisHealthChecker` 通过 `PING` 维护 Redis 健康状态。

当前工厂 `RateLimiterFactory` 只创建单机限流器。分布式限流器需要显式传入 `RedisCommandExecutor`：

```java
RedisCommandExecutor redis = new SpringDataRedisCommandExecutor(stringRedisTemplate);
RateLimiterConfig config = RateLimiterConfig.builder(AlgorithmType.DISTRIBUTED_TOKEN_BUCKET)
        .capacity(1000)
        .ratePerSecond(100.0)
        .window(Duration.ofSeconds(1))
        .build();

RateLimiter limiter = new RedisRateLimiter("api:create-order", config, redis);
```

Redis 故障时可以组合本地降级：

```java
RateLimiter localFallback = new TokenBucketRateLimiter(config.toBuilder()
        .capacity(100)
        .ratePerSecond(10.0)
        .build());
RateLimiter limiter = new DegradingRateLimiter(distributedLimiter, localFallback, redisHealthChecker);
```

## 自适应限流

Phase 4 引入自适应限流核心组件：

- `SystemMetricsCollector` 采集 CPU、堆内存和当前 QPS 快照。
- `PIDController` 根据目标 CPU 利用率计算限流调整比例。
- `AdaptiveRateLimiterScheduler` 将调整比例应用到自适应限流器，并按 min/max QPS 边界裁剪。

PID 调整方向：

| 系统状态 | 调整行为 |
|----------|----------|
| CPU 低于目标值 | 放宽 QPS |
| CPU 高于目标值 | 收紧 QPS |
| 计算结果超过边界 | 限制在 min/max QPS 内 |

自适应调度器可以包装现有单机限流器并动态调整 QPS：

```java
AdaptiveRateLimiterConfig adaptiveConfig = new AdaptiveRateLimiterConfig(
        AlgorithmType.TOKEN_BUCKET,
        100,
        20.0,
        10.0,
        80.0,
        Duration.ofSeconds(1)
);

ConfigurableAdaptiveRateLimiter limiter = ConfigurableAdaptiveRateLimiter.create(adaptiveConfig);
AdaptiveRateLimiterScheduler scheduler = new AdaptiveRateLimiterScheduler(
        new SystemMetricsCollector(() -> (long) limiter.currentQps()),
        new PIDController(0.60, 1.0, 0.0, 0.0),
        List.of(limiter)
);

scheduler.adjust(1.0);
```

这个阶段仍然不引入 Spring `@Scheduled` 自动任务，避免把核心自适应逻辑和框架生命周期耦合在一起。

## Spring 注解接入

Phase 5.1 引入最小注解式接入方式。业务方法可以通过 `@RateLimit` 使用单机限流器：

```java
@RateLimit(
        key = "order:create",
        algorithm = AlgorithmType.TOKEN_BUCKET,
        capacity = 100,
        ratePerSecond = 10.0,
        windowMillis = 1000,
        permits = 1
)
public void createOrder() {
    // business logic
}
```

也可以把规则放到 `application.yml` 或 `application.properties`，注解只保留稳定 key：

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

```properties
ratelimiter.rules[order:create].algorithm=TOKEN_BUCKET
ratelimiter.rules[order:create].capacity=100
ratelimiter.rules[order:create].rate-per-second=10.0
ratelimiter.rules[order:create].window-millis=1000
ratelimiter.rules[order:create].permits=1
```

```java
@RateLimit(key = "order:create")
public void createOrder() {
    // business logic
}
```

规则解析优先级：

1. 如果配置文件中存在同名 key，使用配置文件规则。
2. 如果不存在同名 key，使用注解参数。
3. 如果注解 key 为空，使用 `类名#方法名` 作为 key，并同样先查配置文件。

当前注解接入范围：

- 仅支持本地限流算法。
- 被限流时快速失败并抛出 `RateLimitException`。
- 暂不支持 Java SPI、动态刷新、Redis 分布式模式和自适应模式。

## 文档

- [项目大纲](PROJECT_OUTLINE.md)
- [完整规格书](Distributed-RateLimiter-Spec.md)
