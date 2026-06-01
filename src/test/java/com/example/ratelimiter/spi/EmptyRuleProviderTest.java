package com.example.ratelimiter.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmptyRuleProviderTest {

    @Test
    void alwaysReturnsEmptyRule() {
        EmptyRuleProvider provider = new EmptyRuleProvider();

        assertThat(provider.findRule("order:create")).isEmpty();
    }

    @Test
    void hasDefaultPriority() {
        assertThat(new EmptyRuleProvider().priority()).isEqualTo(0);
    }
}
