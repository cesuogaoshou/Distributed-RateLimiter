package com.example.ratelimiter.exception;

public class RateLimiterConfigException extends IllegalArgumentException {

    public RateLimiterConfigException(String message) {
        super(message);
    }
}
