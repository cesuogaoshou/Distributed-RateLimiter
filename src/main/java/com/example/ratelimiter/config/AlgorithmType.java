package com.example.ratelimiter.config;

public enum AlgorithmType {
    TOKEN_BUCKET,
    LEAKY_BUCKET,
    FIXED_WINDOW,
    SLIDING_WINDOW,
    DISTRIBUTED_TOKEN_BUCKET
}
