package com.example.ratelimiter.spi;

import com.example.ratelimiter.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultRejectHandlerTest {

    @Test
    void throwsRateLimitExceptionWithRejectedKey() {
        DefaultRejectHandler handler = new DefaultRejectHandler();

        assertThatThrownBy(() -> handler.handle("order:create", null))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Rate limit exceeded for key: order:create");
    }

    @Test
    void hasDefaultPriority() {
        assertThat(new DefaultRejectHandler().priority()).isEqualTo(0);
    }
}
