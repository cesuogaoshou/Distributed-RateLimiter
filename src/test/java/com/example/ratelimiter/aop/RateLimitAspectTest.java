package com.example.ratelimiter.aop;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.core.RateLimiterFactory;
import com.example.ratelimiter.exception.RateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void unannotatedMethodIsNotLimited() {
        assertThat(testService.unlimited()).isEqualTo("unlimited");
        assertThat(testService.unlimited()).isEqualTo("unlimited");
        assertThat(testService.unlimited()).isEqualTo("unlimited");
    }

    @Test
    void blankKeyUsesClassAndMethodName() {
        assertThat(testService.defaultKey()).isEqualTo("default");

        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(testService);
        String expectedKey = targetClass.getName() + "#defaultKey";
        assertThatThrownBy(() -> testService.defaultKey())
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining(expectedKey);
    }

    @TestConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        RateLimiterFactory rateLimiterFactory() {
            return new RateLimiterFactory();
        }

        @Bean
        RateLimitAspect rateLimitAspect(RateLimiterFactory rateLimiterFactory) {
            return new RateLimitAspect(rateLimiterFactory);
        }

        @Bean
        TestService testService() {
            return new TestService();
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

        public String unlimited() {
            return "unlimited";
        }

        @RateLimit(capacity = 1, ratePerSecond = 0.0)
        public String defaultKey() {
            return "default";
        }
    }
}
