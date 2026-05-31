package com.example.ratelimiter.aop;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.config.AlgorithmType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitAspectTest {

    @Test
    void rateLimitAnnotationExposesExpectedDefaults() throws NoSuchMethodException {
        Method method = AnnotatedService.class.getDeclaredMethod("limited");
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        assertThat(rateLimit.key()).isEmpty();
        assertThat(rateLimit.algorithm()).isEqualTo(AlgorithmType.TOKEN_BUCKET);
        assertThat(rateLimit.capacity()).isEqualTo(100);
        assertThat(rateLimit.ratePerSecond()).isEqualTo(10.0);
        assertThat(rateLimit.windowMillis()).isEqualTo(1000);
        assertThat(rateLimit.permits()).isEqualTo(1);
    }

    static class AnnotatedService {

        @RateLimit
        String limited() {
            return "ok";
        }
    }
}
