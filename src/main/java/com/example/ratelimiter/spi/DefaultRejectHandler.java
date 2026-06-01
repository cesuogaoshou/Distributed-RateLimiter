package com.example.ratelimiter.spi;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.exception.RateLimitException;

public class DefaultRejectHandler implements RejectHandler {

    @Override
    public void handle(String key, RateLimit rateLimit) {
        throw new RateLimitException("Rate limit exceeded for key: " + key);
    }
}
