package com.example.ratelimiter.spi;

import com.example.ratelimiter.rule.RateLimitRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RuleProviderLoaderTest {

    @Test
    void fallsBackToEmptyProviderWhenNoProvidersExist() {
        RuleProvider provider = new RuleProviderLoader(List.of()).load();

        assertThat(provider).isInstanceOf(EmptyRuleProvider.class);
        assertThat(provider.findRule("missing")).isEmpty();
    }

    @Test
    void choosesHighestPriorityProvider() {
        RuleProvider low = new StaticRuleProvider(5);
        RuleProvider high = new StaticRuleProvider(10);

        RuleProvider provider = new RuleProviderLoader(List.of(low, high)).load();

        assertThat(provider).isSameAs(high);
    }

    private static class StaticRuleProvider implements RuleProvider {

        private final int priority;

        private StaticRuleProvider(int priority) {
            this.priority = priority;
        }

        @Override
        public Optional<RateLimitRule> findRule(String key) {
            return Optional.empty();
        }

        @Override
        public int priority() {
            return priority;
        }
    }
}
