package com.example.ratelimiter.annotation;

import com.example.ratelimiter.config.AlgorithmType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    String key() default "";

    AlgorithmType algorithm() default AlgorithmType.TOKEN_BUCKET;

    long capacity() default 100;

    double ratePerSecond() default 10.0;

    long windowMillis() default 1000;

    int permits() default 1;
}
