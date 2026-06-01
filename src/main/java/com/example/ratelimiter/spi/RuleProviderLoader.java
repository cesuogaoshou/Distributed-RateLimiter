package com.example.ratelimiter.spi;

import java.util.Comparator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

public class RuleProviderLoader {

    private final Iterable<RuleProvider> providers;

    public RuleProviderLoader() {
        this(ServiceLoader.load(RuleProvider.class));
    }

    public RuleProviderLoader(Iterable<RuleProvider> providers) {
        this.providers = Objects.requireNonNull(providers, "providers must not be null");
    }

    public RuleProvider load() {
        return StreamSupport.stream(providers.spliterator(), false)
                .max(Comparator.comparingInt(RuleProvider::priority))
                .orElseGet(EmptyRuleProvider::new);
    }
}
