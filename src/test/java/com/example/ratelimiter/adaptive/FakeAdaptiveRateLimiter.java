package com.example.ratelimiter.adaptive;

class FakeAdaptiveRateLimiter implements AdaptiveRateLimiter {

    private final double minQps;
    private final double maxQps;
    private double currentQps;

    FakeAdaptiveRateLimiter(double currentQps, double minQps, double maxQps) {
        this.currentQps = currentQps;
        this.minQps = minQps;
        this.maxQps = maxQps;
    }

    @Override
    public double currentQps() {
        return currentQps;
    }

    @Override
    public double minQps() {
        return minQps;
    }

    @Override
    public double maxQps() {
        return maxQps;
    }

    @Override
    public void updateQps(double qps) {
        this.currentQps = qps;
    }
}
