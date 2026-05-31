package com.example.ratelimiter.benchmark;

import com.example.ratelimiter.algorithm.FixedWindowRateLimiter;
import com.example.ratelimiter.algorithm.LeakyBucketRateLimiter;
import com.example.ratelimiter.algorithm.SlidingWindowRateLimiter;
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
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class LocalRateLimiterBenchmark {

    @Benchmark
    @Threads(1)
    public boolean tokenBucketSingleThread(BenchmarkState state) {
        return state.tokenBucket.tryAcquire();
    }

    @Benchmark
    @Threads(4)
    public boolean tokenBucketFourThreads(BenchmarkState state) {
        return state.tokenBucket.tryAcquire();
    }

    @Benchmark
    @Threads(8)
    public boolean tokenBucketEightThreads(BenchmarkState state) {
        return state.tokenBucket.tryAcquire();
    }

    @Benchmark
    @Threads(1)
    public boolean leakyBucketSingleThread(BenchmarkState state) {
        return state.leakyBucket.tryAcquire();
    }

    @Benchmark
    @Threads(4)
    public boolean leakyBucketFourThreads(BenchmarkState state) {
        return state.leakyBucket.tryAcquire();
    }

    @Benchmark
    @Threads(8)
    public boolean leakyBucketEightThreads(BenchmarkState state) {
        return state.leakyBucket.tryAcquire();
    }

    @Benchmark
    @Threads(1)
    public boolean fixedWindowSingleThread(BenchmarkState state) {
        return state.fixedWindow.tryAcquire();
    }

    @Benchmark
    @Threads(4)
    public boolean fixedWindowFourThreads(BenchmarkState state) {
        return state.fixedWindow.tryAcquire();
    }

    @Benchmark
    @Threads(8)
    public boolean fixedWindowEightThreads(BenchmarkState state) {
        return state.fixedWindow.tryAcquire();
    }

    @Benchmark
    @Threads(1)
    public boolean slidingWindowSingleThread(BenchmarkState state) {
        return state.slidingWindow.tryAcquire();
    }

    @Benchmark
    @Threads(4)
    public boolean slidingWindowFourThreads(BenchmarkState state) {
        return state.slidingWindow.tryAcquire();
    }

    @Benchmark
    @Threads(8)
    public boolean slidingWindowEightThreads(BenchmarkState state) {
        return state.slidingWindow.tryAcquire();
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        private RateLimiter tokenBucket;
        private RateLimiter leakyBucket;
        private RateLimiter fixedWindow;
        private RateLimiter slidingWindow;

        @Setup(Level.Trial)
        public void setup() {
            tokenBucket = new TokenBucketRateLimiter(config(AlgorithmType.TOKEN_BUCKET));
            leakyBucket = new LeakyBucketRateLimiter(config(AlgorithmType.LEAKY_BUCKET));
            fixedWindow = new FixedWindowRateLimiter(config(AlgorithmType.FIXED_WINDOW));
            slidingWindow = new SlidingWindowRateLimiter(config(AlgorithmType.SLIDING_WINDOW));
        }

        private static RateLimiterConfig config(AlgorithmType algorithm) {
            return RateLimiterConfig.builder(algorithm)
                    .capacity(100_000_000)
                    .ratePerSecond(100_000_000.0)
                    .window(Duration.ofSeconds(1))
                    .build();
        }
    }
}
