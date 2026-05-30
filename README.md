# Distributed RateLimiter

高性能分布式限流中间件项目，目标是系统性实现单机限流、分布式限流、自适应限流、性能基准测试、监控面板和 Spring Boot 接入层。

当前仓库处于 Phase 1 单机限流核心阶段，后续工作以 [PROJECT_OUTLINE.md](PROJECT_OUTLINE.md) 为主路线图，以 [Distributed-RateLimiter-Spec.md](Distributed-RateLimiter-Spec.md) 为完整规格参考。

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

Phase 1: 单机限流核心。

已完成：

- 项目规格文档
- 项目大纲
- Git 忽略规则
- 仓库基础 README
- Maven/Spring Boot 3 工程
- 限流核心接口、配置模型和统计模型
- 令牌桶、漏桶、固定窗口、滑动窗口四种单机算法
- 单元测试和并发测试

下一步：

- 引入 JMH 基准测试
- 补充 Guava/Sentinel 对比入口
- 设计 Redis Lua 分布式限流

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

## 文档

- [项目大纲](PROJECT_OUTLINE.md)
- [完整规格书](Distributed-RateLimiter-Spec.md)
