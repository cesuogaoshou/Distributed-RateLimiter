package com.example.ratelimiter.aop;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.config.RateLimiterConfig;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.core.RateLimiterFactory;
import com.example.ratelimiter.rule.RateLimitProperties;
import com.example.ratelimiter.rule.RateLimitRuleProvider;
import com.example.ratelimiter.spi.RejectHandler;
import com.example.ratelimiter.spi.RejectHandlerLoader;
import com.example.ratelimiter.spi.RuleProvider;
import com.example.ratelimiter.spi.RuleProviderLoader;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

@Aspect
@Component
public class RateLimitAspect {

    private final RateLimiterFactory rateLimiterFactory;
    private final RateLimitRuleProvider ruleProvider;
    private final RejectHandler rejectHandler;
    private final RuleProvider spiRuleProvider;

    public RateLimitAspect(RateLimiterFactory rateLimiterFactory) {
        this(rateLimiterFactory, new RateLimitRuleProvider(new RateLimitProperties()));
    }

    @Autowired
    public RateLimitAspect(RateLimiterFactory rateLimiterFactory, RateLimitRuleProvider ruleProvider) {
        this(
                rateLimiterFactory,
                ruleProvider,
                new RejectHandlerLoader().load(),
                new RuleProviderLoader().load()
        );
    }

    public RateLimitAspect(
            RateLimiterFactory rateLimiterFactory,
            RateLimitRuleProvider ruleProvider,
            RejectHandler rejectHandler) {
        this(rateLimiterFactory, ruleProvider, rejectHandler, new RuleProviderLoader().load());
    }

    public RateLimitAspect(
            RateLimiterFactory rateLimiterFactory,
            RateLimitRuleProvider ruleProvider,
            RejectHandler rejectHandler,
            RuleProvider spiRuleProvider) {
        this.rateLimiterFactory = Objects.requireNonNull(rateLimiterFactory, "rateLimiterFactory must not be null");
        this.ruleProvider = Objects.requireNonNull(ruleProvider, "ruleProvider must not be null");
        this.rejectHandler = Objects.requireNonNull(rejectHandler, "rejectHandler must not be null");
        this.spiRuleProvider = Objects.requireNonNull(spiRuleProvider, "spiRuleProvider must not be null");
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = resolveKey(joinPoint, rateLimit);
        ResolvedRule resolvedRule = resolveRule(key, rateLimit);
        RateLimiter limiter = rateLimiterFactory.getOrCreate(key, resolvedRule.config());
        if (!limiter.tryAcquire(resolvedRule.permits())) {
            rejectHandler.handle(key, rateLimit);
            return null;
        }
        return joinPoint.proceed();
    }

    private ResolvedRule resolveRule(String key, RateLimit rateLimit) {
        return spiRuleProvider.findRule(key)
                .or(() -> ruleProvider.findRule(key))
                .map(rule -> new ResolvedRule(rule.toConfig(), rule.getPermits()))
                .orElseGet(() -> new ResolvedRule(toConfig(rateLimit), rateLimit.permits()));
    }

    private RateLimiterConfig toConfig(RateLimit rateLimit) {
        return RateLimiterConfig.builder(rateLimit.algorithm())
                .capacity(rateLimit.capacity())
                .ratePerSecond(rateLimit.ratePerSecond())
                .window(Duration.ofMillis(rateLimit.windowMillis()))
                .build();
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        if (!rateLimit.key().isBlank()) {
            return rateLimit.key();
        }
        return joinPoint.getSignature().getDeclaringTypeName() + "#" + joinPoint.getSignature().getName();
    }

    private record ResolvedRule(RateLimiterConfig config, int permits) {
    }
}
