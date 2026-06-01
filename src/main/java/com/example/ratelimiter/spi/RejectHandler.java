package com.example.ratelimiter.spi;

import com.example.ratelimiter.annotation.RateLimit;

public interface RejectHandler {

    void handle(String key, RateLimit rateLimit);

    default int priority() {
        return 0;
    }
}
