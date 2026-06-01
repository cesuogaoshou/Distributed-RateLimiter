package com.example.ratelimiter.spi;

import com.example.ratelimiter.annotation.RateLimit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RejectHandlerLoaderTest {

    @Test
    void fallsBackToDefaultHandlerWhenNoProvidersExist() {
        RejectHandler handler = new RejectHandlerLoader(List.of()).load();

        assertThat(handler).isInstanceOf(DefaultRejectHandler.class);
    }

    @Test
    void choosesHighestPriorityHandler() {
        RejectHandler low = new CapturingRejectHandler(5);
        RejectHandler high = new CapturingRejectHandler(10);

        RejectHandler handler = new RejectHandlerLoader(List.of(low, high)).load();

        assertThat(handler).isSameAs(high);
    }

    private static class CapturingRejectHandler implements RejectHandler {

        private final int priority;

        private CapturingRejectHandler(int priority) {
            this.priority = priority;
        }

        @Override
        public void handle(String key, RateLimit rateLimit) {
        }

        @Override
        public int priority() {
            return priority;
        }
    }
}
