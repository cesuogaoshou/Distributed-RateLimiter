package com.example.ratelimiter.aop;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.core.RateLimiterFactory;
import com.example.ratelimiter.exception.RateLimitException;
import com.example.ratelimiter.rule.RateLimitProperties;
import com.example.ratelimiter.rule.RateLimitRule;
import com.example.ratelimiter.rule.RateLimitRuleProvider;
import com.example.ratelimiter.spi.RejectHandler;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = RateLimitAspectTest.TestConfig.class)
class RateLimitAspectTest {

    @Autowired
    private TestService testService;

    @Test
    void annotatedMethodAllowsCallsWithinCapacity() {
        assertThat(testService.limitedTwice()).isEqualTo("limited");
        assertThat(testService.limitedTwice()).isEqualTo("limited");
    }

    @Test
    void annotatedMethodThrowsAfterCapacityIsExhausted() {
        assertThat(testService.rejectAfterOne()).isEqualTo("first");

        assertThatThrownBy(() -> testService.rejectAfterOne())
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Rate limit exceeded for key: aspect:reject-after-one");
    }

    @Test
    void configuredRuleOverridesAnnotationAttributes() {
        assertThat(testService.configuredOverride()).isEqualTo("configured");

        assertThatThrownBy(() -> testService.configuredOverride())
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Rate limit exceeded for key: aspect:configured-override");
    }

    @Test
    void unconfiguredKeyFallsBackToAnnotationAttributes() {
        assertThat(testService.annotationFallback()).isEqualTo("fallback");
        assertThat(testService.annotationFallback()).isEqualTo("fallback");
    }

    @Test
    void unannotatedMethodIsNotLimited() {
        assertThat(testService.unlimited()).isEqualTo("unlimited");
        assertThat(testService.unlimited()).isEqualTo("unlimited");
        assertThat(testService.unlimited()).isEqualTo("unlimited");
    }

    @Test
    void blankKeyUsesDefaultKeyForConfiguredRuleLookup() {
        assertThat(testService.defaultKeyConfigured()).isEqualTo("default");

        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(testService);
        String expectedKey = targetClass.getName() + "#defaultKeyConfigured";
        assertThatThrownBy(() -> testService.defaultKeyConfigured())
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining(expectedKey);
    }

    @Test
    void delegatesRejectedCallToCustomRejectHandler() throws Throwable {
        AtomicInteger rejectCount = new AtomicInteger();
        AtomicReference<String> rejectedKey = new AtomicReference<>();
        RejectHandler rejectHandler = (key, rateLimit) -> {
            rejectedKey.set(key);
            rejectCount.incrementAndGet();
        };
        RateLimitAspect aspect = new RateLimitAspect(
                new RateLimiterFactory(),
                new RateLimitRuleProvider(new RateLimitProperties()),
                rejectHandler
        );
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn(TestService.class.getName());
        when(signature.getName()).thenReturn("rejectAfterOne");
        when(joinPoint.proceed()).thenReturn("first");
        RateLimit rateLimit = TestService.class.getMethod("rejectAfterOne").getAnnotation(RateLimit.class);

        assertThat(aspect.around(joinPoint, rateLimit)).isEqualTo("first");
        assertThat(aspect.around(joinPoint, rateLimit)).isNull();

        assertThat(rejectedKey.get()).isEqualTo("aspect:reject-after-one");
        assertThat(rejectCount.get()).isEqualTo(1);
        verify(joinPoint, times(1)).proceed();
    }

    @TestConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        RateLimiterFactory rateLimiterFactory() {
            return new RateLimiterFactory();
        }

        @Bean
        @Primary
        RateLimitProperties rateLimitProperties() {
            RateLimitProperties properties = new RateLimitProperties();
            Map<String, RateLimitRule> rules = new HashMap<>();
            rules.put("aspect:configured-override", onePermitRule());
            rules.put(TestService.class.getName() + "#defaultKeyConfigured", onePermitRule());
            properties.setRules(rules);
            return properties;
        }

        @Bean
        @Primary
        RateLimitRuleProvider rateLimitRuleProvider(RateLimitProperties properties) {
            return new RateLimitRuleProvider(properties);
        }

        @Bean
        @Primary
        RateLimitAspect rateLimitAspect(
                RateLimiterFactory rateLimiterFactory,
                RateLimitRuleProvider rateLimitRuleProvider) {
            return new RateLimitAspect(rateLimiterFactory, rateLimitRuleProvider);
        }

        @Bean
        TestService testService() {
            return new TestService();
        }

        private static RateLimitRule onePermitRule() {
            RateLimitRule rule = new RateLimitRule();
            rule.setCapacity(1);
            rule.setRatePerSecond(0.0);
            return rule;
        }
    }

    public static class TestService {

        @RateLimit(key = "aspect:limited-twice", capacity = 2, ratePerSecond = 0.0)
        public String limitedTwice() {
            return "limited";
        }

        @RateLimit(key = "aspect:reject-after-one", capacity = 1, ratePerSecond = 0.0)
        public String rejectAfterOne() {
            return "first";
        }

        @RateLimit(key = "aspect:configured-override", capacity = 10, ratePerSecond = 0.0)
        public String configuredOverride() {
            return "configured";
        }

        @RateLimit(key = "aspect:annotation-fallback", capacity = 2, ratePerSecond = 0.0)
        public String annotationFallback() {
            return "fallback";
        }

        public String unlimited() {
            return "unlimited";
        }

        @RateLimit(capacity = 10, ratePerSecond = 0.0)
        public String defaultKeyConfigured() {
            return "default";
        }
    }
}
