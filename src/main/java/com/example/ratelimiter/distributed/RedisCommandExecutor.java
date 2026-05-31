package com.example.ratelimiter.distributed;

import java.util.List;

public interface RedisCommandExecutor {

    List<Long> evalTokenBucket(String key, long capacity, double refillRatePerSecond, long permits, long nowMillis);

    boolean ping();
}
