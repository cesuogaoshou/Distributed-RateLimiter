# 高性能分布式限流中间件 — 完整项目规格书

> **项目定位**：展示并发编程、分布式系统、算法实现、性能优化能力的 Java 基础设施项目
> **技术栈**：Java 17 + Spring Boot 3 + Redis + Netty + JMH
> **工期**：4 周（约 160 小时）
> **开源目标**：GitHub 200+ stars

---

## 一、项目概述

### 1.1 一句话描述

一个支持单机/分布式/自适应三种模式、覆盖四种经典算法、提供 Dashboard 和 SPI 插件机制的高性能限流中间件。

### 1.2 核心差异化卖点

- **不止一种算法**：令牌桶、漏桶、固定窗口、滑动窗口四种算法完整实现
- **自适应限流**：基于系统负载指标动态调整阈值，不是简单的拍脑袋配 QPS
- **和 Sentinel 正面 PK**：写 JMH benchmark，拿数据和 Sentinel/Guava RateLimiter 对比
- **插件化**：支持注解驱动、配置文件、动态规则三种接入方式

### 1.3 和你之前提到的 Agent 项目的关系

Agent 项目的 API 层需要限流保护。你可以在 Agent 项目的 README 里写"接入自研限流中间件，QPS 上限可控"，两个项目形成联动，面试效果翻倍。

---

## 二、完整系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                        接入层                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ 注解驱动      │  │ 配置驱动      │  │ 动态规则（配置中心）│  │
│  │ @RateLimiter  │  │ YAML/Properties│  │ Nacos/Apollo     │  │
│  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘  │
│         │                 │                    │            │
│         └─────────────────┼────────────────────┘            │
│                           ↓                                  │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              SPI 插件层 (Java SPI)                    │    │
│  │  RuleProvider → RuleParser → RateLimiterFactory       │    │
│  └──────────────────────┬──────────────────────────────┘    │
│                         ↓                                    │
├─────────────────────────────────────────────────────────────┤
│                      限流引擎核心                             │
│                                                              │
│  ┌──────────────────────────────────────────────────┐       │
│  │                单机限流引擎                         │       │
│  │  ┌────────┐ ┌────────┐ ┌──────────┐ ┌─────────┐ │       │
│  │  │令牌桶   │ │漏桶    │ │固定窗口   │ │滑动窗口  │ │       │
│  │  └────────┘ └────────┘ └──────────┘ └─────────┘ │       │
│  └──────────────────────┬───────────────────────────┘       │
│                         ↓                                    │
│  ┌──────────────────────────────────────────────────┐       │
│  │              分布式限流引擎                         │       │
│  │  ┌────────────┐  ┌────────────┐  ┌─────────────┐ │       │
│  │  │Redis Lua   │  │集群配额协调 │  │降级策略      │ │       │
│  │  └────────────┘  └────────────┘  └─────────────┘ │       │
│  └──────────────────────┬───────────────────────────┘       │
│                         ↓                                    │
│  ┌──────────────────────────────────────────────────┐       │
│  │              自适应限流引擎                         │       │
│  │  ┌──────────┐  ┌──────────┐  ┌────────────────┐  │       │
│  │  │系统指标   │  │PID控制器 │  │动态阈值调整    │  │       │
│  │  │采集器     │  │          │  │                │  │       │
│  │  └──────────┘  └──────────┘  └────────────────┘  │       │
│  └──────────────────────────────────────────────────┘       │
│                         ↓                                    │
│  ┌──────────────────────────────────────────────────┐       │
│  │                限流结果处理                          │       │
│  │  ┌──────────┐  ┌──────────────┐  ┌─────────────┐ │       │
│  │  │快速失败   │  │排队等待      │  │自定义降级    │ │       │
│  │  └──────────┘  └──────────────┘  └─────────────┘ │       │
│  └──────────────────────────────────────────────────┘       │
├─────────────────────────────────────────────────────────────┤
│                      监控 & 运维层                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Metrics 采集  │  │ Dashboard    │  │ 告警（可选）      │  │
│  │(Micrometer)  │  │(Spring Boot) │  │                  │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 三、模块拆解（逐个击破）

### 模块 1：核心限流算法（Week 1，最重要）

这是整个项目的灵魂，必须做到**每种算法都有单元测试 + 并发安全性证明**。

#### 1.1 令牌桶 (Token Bucket)

**算法描述**：以固定速率向桶中放入令牌，请求到达时取令牌，有令牌则放行，无令牌则拒绝。

**核心数据结构**：
```java
public class TokenBucket {
    private final long capacity;           // 桶容量
    private final double refillRate;       // 令牌放入速率（个/秒）
    private double currentTokens;          // 当前令牌数
    private volatile long lastRefillTime;  // 上次填充时间（纳秒）

    public synchronized boolean tryAcquire(int permits);
    private void refill();                 // 懒填充：按时间差计算应填充的令牌数
}
```

**实现要点**：
- 使用 `synchronized` 保证线程安全（是的，先用最简单的方案）
- 懒填充策略：`currentTokens = min(capacity, currentTokens + (now - lastRefillTime) * refillRate / 1e9)`
- 支持批量获取 permits

**单元测试要求**：
- 突发流量测试：桶满时能否瞬间放过 capacity 个请求
- 持续流量测试：请求速率略高于 refillRate 时，拒绝比例是否符合预期
- 并发测试：10 个线程同时 acquire，验证令牌数不会为负
- 边界测试：capacity=0, refillRate=0, permits=0

#### 1.2 漏桶 (Leaky Bucket)

**算法描述**：请求先入桶，桶以固定速率漏水（处理请求），桶满则拒绝。

**核心数据结构**：
```java
public class LeakyBucket {
    private final long capacity;           // 桶容量
    private final double drainRate;        // 漏水速率（个/秒）
    private double waterLevel;             // 当前水位
    private volatile long lastDrainTime;   // 上次漏水时间

    public synchronized boolean tryAcquire();
    private void drain();                  // 按时间差漏水
}
```

**和令牌桶的对比**（面试必问点）：
| 维度 | 令牌桶 | 漏桶 |
|------|--------|------|
| 突发流量 | 允许（桶里攒的令牌一次用完） | 不允许（强制恒定速率） |
| 流量整形 | 弱 | 强 |
| 适用场景 | 允许短时间突发的 API | 需要严格平滑流量的场景 |
| 实现复杂度 | 低 | 低 |

#### 1.3 固定窗口 (Fixed Window)

**算法描述**：将时间划分为固定窗口（如 1 秒），窗口内计数,超过阈值拒绝。下一个窗口清零。

**核心数据结构**：
```java
public class FixedWindow {
    private final long windowSizeMs;       // 窗口大小
    private final long maxRequests;        // 窗口内最大请求数
    private final AtomicLong counter;      // 当前窗口计数
    private volatile long windowStart;     // 当前窗口起始时间

    public boolean tryAcquire();
}
```

**实现要点**：
- `AtomicLong` + CAS 重置窗口（关键面试点：为什么不用锁？）
- 临界问题处理：两个线程同时发现窗口过期，只有一个能成功重置

**临界问题详解**（这是面试核心深度位）：
```
时间轴：|---- 窗口1 ----|---- 窗口2 ----|
             ↑              ↑
        请求 A (windowStart 切换)   请求 B (还在旧窗口)
        
如果 A 和 B 几乎同时到达窗口边界，B 可能看到旧 windowStart
导致请求被错误地计入新窗口。你的 CAS 逻辑需要处理这种情况。
```

#### 1.4 滑动窗口 (Sliding Window)

**算法描述**：固定窗口的改进，窗口随着时间滑动，解决窗口边界的流量突刺问题。

**实现方案选择**（面试会问为什么选这个）：

**方案 A — 精确滑动窗口（推荐实现）**：
```java
public class SlidingWindow {
    private final long windowSizeMs;           // 窗口大小
    private final long maxRequests;            // 窗口内最大请求数
    private final ConcurrentLinkedDeque<Long> timestamps; // 请求时间戳队列
    
    public synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        // 移除窗口外的旧时间戳
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowSizeMs) {
            timestamps.pollFirst();
        }
        if (timestamps.size() < maxRequests) {
            timestamps.addLast(now);
            return true;
        }
        return false;
    }
}
```

**优缺点**：
- 优点：精确、直观
- 缺点：并发环境下需要同步，每个请求都维护时间戳队列，内存/CPU 开销大

**方案 B — 分段滑动窗口**（用于对比和分析）：
- 将窗口分为 N 个格子，每格独立计数
- 更省内存，但精度降低

**面试可以聊**：为什么选了方案 A？在什么量级下方案 A 会成为瓶颈？如果要优化你会怎么做？

#### 1.5 算法模块的通用接口

```java
public interface RateLimiter {
    /**
     * 尝试获取许可
     * @return true-允许通过, false-被限流
     */
    boolean tryAcquire();
    
    /**
     * 尝试获取多个许可
     */
    boolean tryAcquire(int permits);
    
    /**
     * 获取当前可用许可数（用于监控）
     */
    long availablePermits();
    
    /**
     * 获取限流器统计信息
     */
    RateLimiterStats getStats();
    
    /**
     * 更新限流配置（用于动态调整）
     */
    void updateConfig(RateLimiterConfig config);
}
```

---

### 模块 2：单机限流引擎 & 性能基准测试（Week 1-2）

#### 2.1 限流器工厂

```java
@Component
public class RateLimiterFactory {
    private final ConcurrentHashMap<String, RateLimiter> registry = new ConcurrentHashMap<>();
    
    public RateLimiter getOrCreate(String key, RateLimiterConfig config) {
        return registry.computeIfAbsent(key, k -> create(config));
    }
    
    private RateLimiter create(RateLimiterConfig config) {
        return switch (config.getAlgorithm()) {
            case TOKEN_BUCKET   -> new TokenBucket(config);
            case LEAKY_BUCKET   -> new LeakyBucket(config);
            case FIXED_WINDOW   -> new FixedWindow(config);
            case SLIDING_WINDOW -> new SlidingWindow(config);
        };
    }
}
```

**面试点**：`ConcurrentHashMap.computeIfAbsent` 的原理？如果 create 方法很慢会怎样？如何优化（双重检查锁 + volatile）？

#### 2.2 并发安全分析矩阵

| 算法 | 关键操作 | 并发方案 | 为什么 |
|------|---------|---------|--------|
| 令牌桶 | refill + acquire | `synchronized` | 读写耦合，简单可靠 |
| 漏桶 | drain + acquire | `synchronized` | 同上 |
| 固定窗口 | counter++ / 窗口重置 | `AtomicLong` + CAS 重置 | 读多写少，CAS 性能更好 |
| 滑动窗口 | 队列操作 + 计数 | `synchronized` | 队列操作本身非线程安全 |

#### 2.3 JUnit 5 单元测试矩阵

每种算法至少覆盖7个维度：
- 基本功能：正常放行
- 触发限流：超过阈值被拒绝
- 并发正确性：10 线程并发，总数不超阈值
- 时间语义：sleep 等窗口过期后能否恢复
- 精度验证：100 个请求，统计通过的速率偏差 < 5%
- 边界条件：capacity=0、单 permits、大量 permits
- 配置热更新：运行时改阈值立即生效

---

### 模块 3：JMH 性能基准测试（Week 2）

#### 3.1 测试维度

| 测试 | JMH 参数 | 目的 |
|------|---------|------|
| 单线程吞吐量 | `@Threads(1)` | 测量每种算法的基本开销 |
| 4 线程吞吐量 | `@Threads(4)` | 模拟真实并发场景 |
| 8/16 线程竞态 | `@Threads(8)` `@Threads(16)` | 锁竞争下的性能退化 |
| 内存分配率 | `-prof gc` | 测量 GC 压力 |
| 对比 Guava RateLimiter | 相同配置 | 和业界标杆对比 |
| 对比 Sentinel | 相同配置 | 大厂方案对比 |

#### 3.2 示例 JMH 代码

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class TokenBucketBenchmark {
    private TokenBucket bucket;
    
    @Setup
    public void setup() {
        bucket = new TokenBucket(new RateLimiterConfig(
            100_000,  // capacity: 10 万
            10_000    // refillRate: 1 万/秒
        ));
    }
    
    @Benchmark
    @Threads(4)
    public boolean benchmark4Threads() {
        return bucket.tryAcquire();
    }
    
    @Benchmark
    @Threads(8)
    public boolean benchmark8Threads() {
        return bucket.tryAcquire();
    }
}
```

#### 3.3 预期结果（放入 README）

一个标准测试表：

```
Benchmark                          Mode  Cnt      Score      Error  Units
TokenBucket.benchmark1Thread      thrpt   10  45,234,567 ± 123,456  ops/s
TokenBucket.benchmark4Threads     thrpt   10  38,123,456 ± 234,567  ops/s
TokenBucket.benchmark8Threads     thrpt   10  28,456,789 ± 345,678  ops/s
GuavaRateLimiter.benchmark4Thread thrpt   10  41,234,567 ± 156,789  ops/s
Sentinel.benchmark4Threads        thrpt   10  35,678,901 ± 234,567  ops/s
```

**不加数据也能做项目，但有数据的 README 面试官会认真看。**

---

### 模块 4：分布式限流引擎（Week 2-3）

#### 4.1 基于 Redis 的分布式令牌桶

**为什么需要分布式限流**：
单机限流只能控制本机流量，集群场景下需要全局维度的流量控制。比如你集群部署了 10 个节点，总 QPS 限制 1000，每个节点平均分 100，但如果流量不均匀，有的节点 200 有的节点 50，单机限流就失效了。

**技术方案**：
用 Redis + Lua 脚本实现原子操作。

**Lua 脚本**（这是核心代码，面试会问 Lua 为什么能用在这里）：
```lua
-- ratelimit.lua
-- KEYS[1]: 限流器的 Redis key
-- ARGV[1]: 桶容量
-- ARGV[2]: 填充速率（令牌/秒）
-- ARGV[3]: 当前时间戳（毫秒）
-- ARGV[4]: 请求的令牌数

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

-- 从 Redis 读取上次状态
local last_tokens = tonumber(redis.call('GET', key .. ':tokens'))
local last_time = tonumber(redis.call('GET', key .. ':time'))

if last_tokens == nil then
    -- 首次初始化
    last_tokens = capacity
    last_time = now
end

-- 计算新增令牌
local delta = math.max(0, now - last_time) * rate / 1000.0
local new_tokens = math.min(capacity, last_tokens + delta)

-- 判断是否足够
if new_tokens >= requested then
    new_tokens = new_tokens - requested
    redis.call('SET', key .. ':tokens', new_tokens)
    redis.call('SET', key .. ':time', now)
    redis.call('EXPIRE', key .. ':tokens', 60)
    redis.call('EXPIRE', key .. ':time', 60)
    return 1  -- 允许
else
    return 0  -- 拒绝
end
```

**Java 调用代码**：
```java
@Component
public class RedisRateLimiter {
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;
    
    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/ratelimit.lua"));
        script.setResultType(Long.class);
    }
    
    public boolean tryAcquire(String key, long capacity, double rate, int permits) {
        Long result = redisTemplate.execute(
            script,
            Collections.singletonList(key),
            String.valueOf(capacity),
            String.valueOf(rate),
            String.valueOf(System.currentTimeMillis()),
            String.valueOf(permits)
        );
        return result != null && result == 1L;
    }
}
```

**面试追问链条**：
1. "为什么用 Lua 脚本？" → Redis 单线程执行 Lua，保证原子性
2. "不用 Lua 会有什么问题？" → 读-判断-写 三个命令不是原子的，会超发
3. "Lua 脚本执行时间过长怎么办？" → Redis 会阻塞，控制脚本复杂度
4. "Redis 挂了怎么办？" → 降级到单机限流（见 4.3）

#### 4.2 集群配额协调

```
场景：3 个服务节点，总 QPS=300

方案对比（面试能聊这个很加分）：

┌─────────────────────────────────────────────────────┐
│ 方案 A：均分（简单但僵硬）                              │
│   Node1: 100 QPS, Node2: 100 QPS, Node3: 100 QPS     │
│   问题：Node1 打满 100，Node2 只用了 10，浪费了 90     │
├─────────────────────────────────────────────────────┤
│ 方案 B：Redis 全局计数（你实现的）                      │
│   所有节点共享一个 Redis 计数器                        │
│   优点：利用率 100%                                   │
│   缺点：每次请求都要调 Redis                          │
├─────────────────────────────────────────────────────┤
│ 方案 C：本地配额 + 动态重分配（加分项）                 │
│   每节点先领 30 QPS 本地配额                           │
│   配额不够时向 Redis 申请更多                           │
│   定期归还多余配额                                     │
│   优点：减少 Redis 调用，兼顾灵活性和性能               │
└─────────────────────────────────────────────────────┘
```

**强烈建议实现方案 C**，然后把三种方案的架构图和 trade-off 分析写进 README。这是面试时的绝对加分位。

#### 4.3 降级策略（必做）

当 Redis 不可用时，分布式限流器需要降级。

```java
public class DegradingRateLimiter implements RateLimiter {
    private final RedisRateLimiter distributed;
    private final RateLimiter local;  // 单机限流器兜底
    private volatile boolean distributedAvailable = true;
    
    @Override
    public boolean tryAcquire() {
        if (distributedAvailable) {
            try {
                return distributed.tryAcquire();
            } catch (RedisException e) {
                distributedAvailable = false;
                log.warn("Redis unavailable, fallback to local rate limiter");
                // 这里可以加告警通知
            }
        }
        return local.tryAcquire();
    }
    
    // 后台线程定期探测 Redis 是否恢复
    @Scheduled(fixedDelay = 5000)
    public void healthCheck() {
        try {
            redisTemplate.execute("PING");
            distributedAvailable = true;
        } catch (Exception ignored) {}
    }
}
```

---

### 模块 5：自适应限流引擎（Week 3）

这是整个项目最大的差异化卖点。大部分限流项目都是配死阈值，但你能做动态调整。

#### 5.1 核心思想

传统限流："这个 API 限制 1000 QPS"（拍脑袋的值）
自适应限流："系统 CPU > 80% 时自动收紧限流阈值，< 50% 时自动放宽"

```
                  ┌──────────────┐
                  │  系统指标采集  │
                  │  CPU/Mem/QPS  │
                  └──────┬───────┘
                         ↓
                  ┌──────────────┐
                  │  PID 控制器   │
                  │  误差 → 调整量 │
                  └──────┬───────┘
                         ↓
                  ┌──────────────┐
                  │  更新限流阈值  │
                  │  RateLimiter  │
                  │  .updateConfig│
                  └──────────────┘
```

#### 5.2 系统指标采集器

```java
@Component
public class SystemMetricsCollector {
    private final OperatingSystemMXBean osBean;
    
    public SystemMetricsCollector() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
    }
    
    public SystemMetrics collect() {
        return SystemMetrics.builder()
            .cpuLoad(getCpuLoad())
            .heapUsed(getHeapUsed())
            .heapMax(getHeapMax())
            .currentQps(getCurrentQps())
            .build();
    }
    
    private double getCpuLoad() {
        // com.sun.management.OperatingSystemMXBean 提供
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            return sunBean.getCpuLoad();  // 0.0 ~ 1.0
        }
        return -1;
    }
    
    // getCurrentQps 从 Micrometer 的 Counter 中获取
    private long getCurrentQps() {
        // ... 基于滑动窗口统计过去 1 秒的请求数
    }
}
```

#### 5.3 PID 控制器（关键算法）

```java
public class PIDController {
    private final double kp, ki, kd;     // PID 三个系数
    private final double setpoint;        // 目标值（如 CPU 目标 60%）
    private double integral = 0;          // 积分累积
    private double lastError = 0;         // 上次误差
    private long lastTime;                // 上次计算时间
    
    /**
     * @param setpoint 目标 CPU 使用率 (0.6)
     * @param kp 比例系数
     * @param ki 积分系数
     * @param kd 微分系数
     */
    public PIDController(double setpoint, double kp, double ki, double kd) {
        this.setpoint = setpoint;
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
        this.lastTime = System.nanoTime();
    }
    
    /**
     * 计算调整量
     * @param currentValue 当前 CPU 使用率
     * @return 限流阈值的调整比例，>0 放宽，<0 收紧
     */
    public synchronized double calculate(double currentValue) {
        long now = System.nanoTime();
        double dt = (now - lastTime) / 1e9;
        if (dt <= 0) return 0;
        
        double error = setpoint - currentValue;
        integral += error * dt;
        double derivative = (error - lastError) / dt;
        
        lastError = error;
        lastTime = now;
        
        return kp * error + ki * integral + kd * derivative;
    }
}
```

#### 5.4 自适应限流调度器

```java
@Component
public class AdaptiveRateLimiterScheduler {
    private final SystemMetricsCollector metricsCollector;
    private final PIDController pidController;
    private final RateLimiterFactory factory;
    
    public AdaptiveRateLimiterScheduler(
            SystemMetricsCollector metricsCollector,
            RateLimiterFactory factory) {
        this.metricsCollector = metricsCollector;
        this.factory = factory;
        // 目标：CPU 保持在 60%
        this.pidController = new PIDController(0.6, 1.0, 0.1, 0.05);
    }
    
    @Scheduled(fixedDelay = 1000)  // 每秒调整一次
    public void adjust() {
        SystemMetrics metrics = metricsCollector.collect();
        double adjustment = pidController.calculate(metrics.getCpuLoad());
        
        // 调整所有自适应限流器的阈值
        factory.getAdaptiveLimiters().forEach(limiter -> {
            double newQps = limiter.getCurrentQps() * (1 + adjustment);
            newQps = Math.max(1, Math.min(newQps, limiter.getMaxQps()));
            limiter.updateConfig(limiter.getConfig().withQps((long) newQps));
        });
    }
}
```

---

### 模块 6：SPI 插件机制 & 接入层（Week 3-4）

#### 6.1 注解驱动接入

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String key() default "";               // 限流 key，默认使用方法名
    Algorithm algorithm() default Algorithm.TOKEN_BUCKET;
    long capacity() default 100;
    double rate() default 10;              // 令牌/秒
    int permits() default 1;               // 每次消耗令牌数
    Mode mode() default Mode.LOCAL;        // LOCAL / DISTRIBUTED / ADAPTIVE
    long waitTimeoutMs() default 0;        // 排队超时，0=快速失败
}
```

**AOP 切面实现**：

```java
@Aspect
@Component
public class RateLimitAspect {
    private final RateLimiterFactory factory;
    
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String key = resolveKey(rateLimit.key(), pjp);
        RateLimiter limiter = factory.getOrCreate(key, buildConfig(rateLimit));
        
        if (rateLimit.waitTimeoutMs() > 0) {
            // 排队等待模式
            if (!limiter.tryAcquire(rateLimit.waitTimeoutMs(), TimeUnit.MILLISECONDS)) {
                throw new RateLimitException("Too many requests, please retry later");
            }
        } else {
            // 快速失败模式
            if (!limiter.tryAcquire()) {
                throw new RateLimitException("Too many requests");
            }
        }
        
        return pjp.proceed();
    }
}
```

**使用示例**：
```java
@RestController
public class OrderController {
    
    @RateLimit(key = "createOrder", algorithm = Algorithm.TOKEN_BUCKET, 
               capacity = 1000, rate = 100, mode = Mode.DISTRIBUTED)
    @PostMapping("/order")
    public Result createOrder(@RequestBody OrderDTO dto) {
        // ...
    }
}
```

#### 6.2 SPI 扩展点

```java
// 扩展点 1：自定义规则提供者
public interface RuleProvider {
    List<RateLimiterConfig> loadRules();
    int priority();  // 优先级，值越小越先执行
}

// 扩展点 2：自定义限流算法
public interface RateLimiterAlgorithm {
    String name();                    // 算法名称
    RateLimiter create(RateLimiterConfig config);
}

// 扩展点 3：自定义限流结果处理器
public interface RejectHandler {
    void handle(String key, RateLimiterConfig config);
    int priority();
}
```

**SPI 注册文件**（`META-INF/services/`）：

`META-INF/services/com.ratelimit.spi.RuleProvider`:
```
com.ratelimit.provider.YamlRuleProvider
com.ratelimit.provider.NacosRuleProvider
```

---

### 模块 7：Dashboard 监控面板（Week 4）

#### 7.1 功能列表

- 实时 QPS 折线图（每个限流器单独一条线）
- 通过/拒绝 比例饼图
- 限流器配置一览表
- 单个限流器的历史记录
- 一键切换算法/修改阈值

#### 7.2 技术选型

| 组件 | 选择 | 原因 |
|------|------|------|
| 后端 | Spring Boot 3 + WebFlux | 已经是 Spring Boot，复用 |
| 指标采集 | Micrometer + 自定义 Gauge | Spring Boot 生态标准 |
| 实时推送 | SSE (Server-Sent Events) | 比 WebSocket 简单，Agent 项目也用这个 |
| 前端 | 纯 HTML + ECharts | 简单够用，不需要脚手架 |
| 图表 | ECharts（CDN 引入） | 一行 `<script>` 搞定 |

#### 7.3 指标端点

```java
@RestController
@RequestMapping("/actuator/ratelimit")
public class RateLimitMetricsController {
    
    @GetMapping("/metrics")
    public Map<String, RateLimiterStats> getAllMetrics() {
        return factory.getAllStats();
    }
    
    @GetMapping("/metrics/{key}")
    public RateLimiterStats getMetric(@PathVariable String key) { ... }
    
    @GetMapping(value = "/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, RateLimiterStats>>> stream() {
        // 每秒推送一次所有限流器的最新统计
        return Flux.interval(Duration.ofSeconds(1))
            .map(i -> ServerSentEvent.builder(factory.getAllStats()).build());
    }
}
```

---

### 模块 8：与 Sentinel 的正面对比分析（Week 4）

这是 README 的压轴部分，让面试官觉得你不是"做了一个玩具"。

#### 8.1 对比维度

| 维度 | RateLimit（你的） | Sentinel | Guava RateLimiter |
|------|------------------|----------|-------------------|
| 限流算法 | 4 种 | 2 种（匀速排队 + 预热） | 1 种（令牌桶）|
| 分布式 | Redis Lua | 基于 Token Server | ❌ 不支持 |
| 自适应 | PID 控制器 | 基于系统负载规则 | ❌ 不支持 |
| 接入方式 | 注解 + 配置 + 动态 | 注解 + 控制台 | 代码硬编码 |
| Dashboard | 自带（ECharts） | Sentinel Dashboard | ❌ 不提供 |
| 性能（4 线程） | xxx ops/s（填入你的数据）| xxx ops/s | xxx ops/s |
| 学习成本 | 低 | 中 | 低 |
| 生产可用性 | 练手项目 | ✅ 生产验证 | ✅ 生产验证 |

#### 8.2 劣势坦诚（面试官喜欢）

> "本项目的限流功能目前不支持集群流控下的精确一致性（CAP 中的 C），在 Redis 故障降级期间可能出现短暂的限流失效。Sentinel 和专业的限流网关（如 Kong、APISIX）在生产环境的可靠性上更胜一筹。"

---

## 四、4 周速通时间表

### Week 1：核心算法 + 单元测试

| 天 | 任务 | 产出 | 预估用时 |
|----|------|------|---------|
| 1 | 项目初始化 + 通用接口定义 | Spring Boot 工程，RateLimiter 接口 | 4h |
| 2 | 令牌桶实现 + 单元测试 | TokenBucket + 7 个测试用例 | 6h |
| 3 | 漏桶实现 + 单元测试 | LeakyBucket + 7 个测试用例 | 5h |
| 4 | 固定窗口实现 + 单元测试 + 临界问题处理 | FixedWindow + CAS 分析文档 | 6h |
| 5 | 滑动窗口实现 + 单元测试 | SlidingWindow + 7 个测试用例 | 6h |
| 6 | 四种算法横向对比测试 + RateLimiterFactory | 工厂 + 切换测试 | 5h |
| 7 | **休息/查漏补缺** | — | — |

### Week 2：JMH + 分布式

| 天 | 任务 | 产出 | 预估用时 |
|----|------|------|---------|
| 1 | JMH 环境搭建 + 单线程基准测试 | 4 种算法的单线程 throughput | 5h |
| 2 | 4/8/16 线程 + 对比 Guava/Sentinel | 完整 JMH 报告 | 6h |
| 3 | Redis Lua 脚本编写 + 调试 | 分布式令牌桶 Lua | 6h |
| 4 | RedisRateLimiter Java 封装 | 和单机版统一的接口 | 5h |
| 5 | 降级策略 + 健康检查 | DegradingRateLimiter | 5h |
| 6 | 集群配额协调（方案 C）| 本地配额 + 动态重分配 | 6h |
| 7 | **休息** | — | — |

### Week 3：自适应 + SPI

| 天 | 任务 | 产出 | 预估用时 |
|----|------|------|---------|
| 1 | 系统指标采集器 | JMX -> SystemMetrics | 4h |
| 2 | PID 控制器实现 + 调参 | PIDController + 调参实验 | 6h |
| 3 | 自适应调度器 | AdaptiveRateLimiterScheduler | 5h |
| 4 | 注解 @RateLimit + AOP | 注解驱动接入 | 5h |
| 5 | SPI 扩展点 + YAML 配置驱动 | RuleProvider + META-INF/services | 5h |
| 6 | 集成测试：三种接入方式同时工作 | 完整链路测试 | 5h |
| 7 | **休息** | — | — |

### Week 4：Dashboard + 收尾

| 天 | 任务 | 产出 | 预估用时 |
|----|------|------|---------|
| 1 | Micrometer 指标采集 | 所有 Metric 接入 | 4h |
| 2 | SSE 指标推送端点 | 实时数据流 | 4h |
| 3 | Dashboard 前端（ECharts） | 可视化面板 | 6h |
| 4 | 与 Sentinel 对比分析 + 压力测试 | 对比报告 | 5h |
| 5 | README 写作（架构图 + 压测报告 + 快速开始） | 完整 README | 6h |
| 6 | 录制 demo 视频 + 写技术博客大纲 | demo + 博客稿 | 5h |
| 7 | GitHub 开源 + 推广（掘金/知乎/牛客） | 仓库上架 | 3h |

---

## 五、README 结构模板

每个部分必须要写，面试官会看的：

```markdown
# RateLimit — 高性能分布式限流中间件

## 1. 项目简介
（一句话 + 特性列表 + 徽章：build passing / coverage 85% / license MIT）

## 2. 架构图
（ASCII 架构图放这里，和上面的一样）

## 3. 快速开始
### 3.1 Maven 依赖
### 3.2 最简示例（3 行代码接入）
### 3.3 注解使用
### 3.4 Docker Compose 启动 Redis + Dashboard

## 4. 四种限流算法对比
（每种算法配图 + 一句话适用场景 + 你测出来的 JMH 数据）

## 5. JMH 压测报告
（吞吐量表格 + 延迟分布 + GC 影响 + 和 Sentinel/Guava 对比）

## 6. 分布式限流设计
（Lua 脚本全文 + 集群配额分配策略对比 + 降级策略流程图）

## 7. 自适应限流
（PID 控制器原理图 + 调参实验数据）

## 8. 监控 Dashboard
（截图或 GIF 动图）

## 9. 和 Sentinel 的对比
（优劣势坦诚对比表）

## 10. Roadmap
（计划中但还没做的功能，显得有长期维护意愿）

## 11. 参考资料
（TCP Vegas 论文、Sentinel 源码分析文章、Redis Lua 编程指南）
```

---

## 六、面试复盘稿模板

面试官看着你的简历问这个项目时，你必须能流利地回答以下问题：

### 6.1 项目介绍（30 秒电梯演讲）

> "我做了一个分布式限流中间件，支持令牌桶、漏桶、固定窗口和滑动窗口四种算法。除了单机版，还做了基于 Redis Lua 的分布式限流和集群配额动态分配。最大的亮点是做了自适应限流——基于系统负载用 PID 控制器动态调整阈值，不做拍脑袋的固定 QPS。和 Sentinel 做了 JMH 对比测试，单机 4 线程令牌桶吞吐量是 xx 万/s。"

### 6.2 核心面试题 & 标准答案

**Q1：为什么用 synchronized 而不是 ReentrantLock？**

> "令牌桶的 refill 和 acquire 是强耦合的读-计算-写操作，synchronized 在低竞争下和 Lock 性能几乎一样（JDK 17 偏向锁优化），而且代码更简洁。如果未来竞争加剧，我测过 JMH 数据，可以用 ReentrantLock 替换，改动仅限于 TokenBucket 内部。"

**Q2：分布式限流中 Redis Lua 脚本为什么要用 Lua？**

> "Redis 单线程执行 Lua 脚本，保证了多个 Redis 命令的原子性。不用 Lua 的话，GET→判断→SET 三个命令之间会有竞态，导致超发。Lua 的代价是脚本执行期间 Redis 会阻塞，所以我控制了脚本复杂度——仅包含加减乘除和 4 次 Redis 调用。"

**Q3：Redis 挂了你的分布式限流怎么办？**

> "自动降级到单机限流。有个健康检查线程每 5 秒 ping Redis，恢复后自动切回分布式模式。降级期间的限流阈值用的是静态配置的降级 QPS。但我也在 README 的不足里坦诚写了——降级期间可能出现短暂的流量不均匀。"

**Q4：你测过和 Sentinel 的性能对比吗？结果如何？**

> "测过。单线程差别不大，4 线程下我的实现稍快 5-10%，但 Sentinel 胜在资源使用和成熟度。我的优化点主要是减少了锁粒度——比如固定窗口用了 AtomicLong + CAS 而不是全局锁。具体数据在 README 的 JMH 章节。"

**Q5：自适应限流的 PID 系数是怎么调的？**

> "先手动调 Kp，让系统在目标附近振荡但不发散；然后加 Ki 消除稳态误差；最后加 Kd 抑制振荡。最终参数是 Kp=1.0, Ki=0.1, Kd=0.05。我用 JMH 模拟了不同负载模式验证收敛速度和稳定性，数据在 README 里有。"

---

## 七、核心注意事项

1. **README 比代码更重要**——面试官先看 README，写得好才看代码。架构图、压测数据、快速开始三样必须一眼看到
2. **commit 必须干净**——按模块拆分 commit（"feat: implement token bucket algorithm"、"test: add concurrent safety tests"），不要一个大 commit 堆完
3. **JMH 数据一定要真实**——不能编，自己跑出来的数据。编的数据面试追问就露馅
4. **劣势坦诚写**——"生产环境不建议直接使用，推荐 Sentinel"这种话不仅不丢分，反而显得你懂生产
5. **把代码推进 GitHub 的前一天**，把你的学校邮箱和姓名 commit 全改成公开的，`git log` 要有完整时间线
6. **两个项目放同一个 GitHub 账号**下，面试官点进你的 GitHub 一眼看到两个高质量仓库
