package com.example.ratelimiter.aop;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.core.RateLimiterFactory;
import com.example.ratelimiter.exception.RateLimitException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

@Aspect
@Component
public class RateLimitAspect {

    private final RateLimiterFactory rateLimiterFactory;

    public RateLimitAspect(RateLimiterFactory rateLimiterFactory) {
        this.rateLimiterFactory = Objects.requireNonNull(rateLimiterFactory, "rateLimiterFactory must not be null");
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = resolveKey(joinPoint, rateLimit);
        RateLimiterConfig config = RateLimiterConfig.builder(rateLimit.algorithm())
                .capacity(rateLimit.capacity())
                .ratePerSecond(rateLimit.ratePerSecond())
                .window(Duration.ofMillis(rateLimit.windowMillis()))
                .build();
        RateLimiter limiter = rateLimiterFactory.getOrCreate(key, config);
        if (!limiter.tryAcquire(rateLimit.permits())) {
            throw new RateLimitException("Rate limit exceeded for key: " + key);
        }
        return joinPoint.proceed();
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        if (!rateLimit.key().isBlank()) {
            return rateLimit.key();
        }
        return joinPoint.getSignature().getDeclaringTypeName() + "#" + joinPoint.getSignature().getName();
    }
}
