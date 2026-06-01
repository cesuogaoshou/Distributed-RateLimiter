package com.example.ratelimiter.benchmark;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.example.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.example.ratelimiter.config.AlgorithmType;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class ComparisonRateLimiterBenchmark {

    @Benchmark
    @Threads(1)
    public boolean projectTokenBucketSingleThread(BenchmarkState state) {
        return state.projectTokenBucket.tryAcquire();
    }

    @Benchmark
    @Threads(1)
    public boolean guavaRateLimiterSingleThread(BenchmarkState state) {
        return state.guavaRateLimiter.tryAcquire();
    }

    @Benchmark
    @Threads(4)
    public boolean projectTokenBucketFourThreads(BenchmarkState state) {
        return state.projectTokenBucket.tryAcquire();
    }

    @Benchmark
    @Threads(4)
    public boolean guavaRateLimiterFourThreads(BenchmarkState state) {
        return state.guavaRateLimiter.tryAcquire();
    }

    @Benchmark
    @Threads(1)
    public boolean sentinelSingleThread(BenchmarkState state) {
        return state.tryAcquireSentinel();
    }

    @Benchmark
    @Threads(4)
    public boolean sentinelFourThreads(BenchmarkState state) {
        return state.tryAcquireSentinel();
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        private static final long CAPACITY = 100_000_000L;
        private static final double RATE_PER_SECOND = 100_000_000.0;
        private static final String SENTINEL_RESOURCE = "comparison-token-bucket";

        private RateLimiter projectTokenBucket;
        private com.google.common.util.concurrent.RateLimiter guavaRateLimiter;

        @Setup(Level.Trial)
        public void setup() {
            projectTokenBucket = new TokenBucketRateLimiter(RateLimiterConfig.builder(AlgorithmType.TOKEN_BUCKET)
                    .capacity(CAPACITY)
                    .ratePerSecond(RATE_PER_SECOND)
                    .window(Duration.ofSeconds(1))
                    .build());
            guavaRateLimiter = com.google.common.util.concurrent.RateLimiter.create(RATE_PER_SECOND);
            configureSentinelRule();
        }

        public boolean tryAcquireSentinel() {
            Entry entry = null;
            try {
                entry = SphU.entry(SENTINEL_RESOURCE);
                return true;
            } catch (BlockException blocked) {
                return false;
            } finally {
                if (entry != null) {
                    entry.exit();
                }
            }
        }

        private static void configureSentinelRule() {
            FlowRule rule = new FlowRule();
            rule.setResource(SENTINEL_RESOURCE);
            rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
            rule.setCount(RATE_PER_SECOND);
            FlowRuleManager.loadRules(List.of(rule));
        }
    }
}
