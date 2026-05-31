package com.example.ratelimiter.distributed;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

public class SpringDataRedisCommandExecutor implements RedisCommandExecutor {

    private static final String TOKEN_BUCKET_SCRIPT = """
            local bucket_key = KEYS[1]
            local tokens_key = bucket_key .. ':tokens'
            local timestamp_key = bucket_key .. ':timestamp'

            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local permits = tonumber(ARGV[3])
            local now = tonumber(ARGV[4])

            local current_tokens = tonumber(redis.call('GET', tokens_key))
            if current_tokens == nil then
                current_tokens = capacity
            end

            local last_refill = tonumber(redis.call('GET', timestamp_key))
            if last_refill == nil then
                last_refill = now
            end

            local elapsed = math.max(0, now - last_refill) / 1000
            local refill = elapsed * refill_rate
            current_tokens = math.min(capacity, current_tokens + refill)

            local allowed = 0
            if current_tokens >= permits then
                current_tokens = current_tokens - permits
                allowed = 1
            end

            redis.call('SET', tokens_key, current_tokens)
            redis.call('SET', timestamp_key, now)
            redis.call('PEXPIRE', tokens_key, 60000)
            redis.call('PEXPIRE', timestamp_key, 60000)

            return { allowed, math.floor(current_tokens) }
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;

    public SpringDataRedisCommandExecutor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, List.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Long> evalTokenBucket(String key, long capacity, double refillRatePerSecond, long permits, long nowMillis) {
        try {
            return (List<Long>) redisTemplate.execute(
                    tokenBucketScript,
                    List.of(key),
                    Long.toString(capacity),
                    Double.toString(refillRatePerSecond),
                    Long.toString(permits),
                    Long.toString(nowMillis)
            );
        } catch (RuntimeException ex) {
            throw new RedisCommandException("failed to execute redis token bucket script", ex);
        }
    }

    @Override
    public boolean ping() {
        try {
            String result = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(result);
        } catch (RedisConnectionFailureException ex) {
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
