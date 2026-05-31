package com.example.ratelimiter.distributed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakeRedisCommandExecutor implements RedisCommandExecutor {

    private final Map<String, BucketState> buckets = new HashMap<>();
    private boolean healthy = true;
    private boolean failCommands;

    @Override
    public synchronized List<Long> evalTokenBucket(String key, long capacity, double refillRatePerSecond, long permits, long nowMillis) {
        if (failCommands) {
            throw new RedisCommandException("simulated redis failure");
        }
        BucketState state = buckets.computeIfAbsent(key, ignored -> new BucketState(capacity, nowMillis));
        long elapsedMillis = Math.max(0, nowMillis - state.lastRefillMillis);
        double refill = elapsedMillis / 1000.0 * refillRatePerSecond;
        state.tokens = Math.min(capacity, state.tokens + refill);
        state.lastRefillMillis = nowMillis;

        long allowed = 0;
        if (state.tokens >= permits) {
            state.tokens -= permits;
            allowed = 1;
        }
        return List.of(allowed, (long) state.tokens);
    }

    @Override
    public boolean ping() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public void setFailCommands(boolean failCommands) {
        this.failCommands = failCommands;
    }

    private static final class BucketState {
        private double tokens;
        private long lastRefillMillis;

        private BucketState(double tokens, long lastRefillMillis) {
            this.tokens = tokens;
            this.lastRefillMillis = lastRefillMillis;
        }
    }
}
