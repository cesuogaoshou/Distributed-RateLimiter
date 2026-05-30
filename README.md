# Distributed RateLimiter

高性能分布式限流中间件项目，目标是系统性实现单机限流、分布式限流、自适应限流、性能基准测试、监控面板和 Spring Boot 接入层。

当前仓库处于项目启动阶段，后续工作以 [PROJECT_OUTLINE.md](PROJECT_OUTLINE.md) 为主路线图，以 [Distributed-RateLimiter-Spec.md](Distributed-RateLimiter-Spec.md) 为完整规格参考。

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

Phase 0: 项目初始化。

已完成：

- 项目规格文档
- 项目大纲
- Git 忽略规则
- 仓库基础 README

下一步：

- 初始化 Maven/Spring Boot 工程
- 定义限流核心接口和配置模型
- 实现四种单机限流算法
- 建立单元测试和并发测试矩阵

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

## 文档

- [项目大纲](PROJECT_OUTLINE.md)
- [完整规格书](Distributed-RateLimiter-Spec.md)
