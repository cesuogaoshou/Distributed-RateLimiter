package com.example.ratelimiter.spi;

import java.util.Comparator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

public class RejectHandlerLoader {

    private final Iterable<RejectHandler> handlers;

    public RejectHandlerLoader() {
        this(ServiceLoader.load(RejectHandler.class));
    }

    public RejectHandlerLoader(Iterable<RejectHandler> handlers) {
        this.handlers = Objects.requireNonNull(handlers, "handlers must not be null");
    }

    public RejectHandler load() {
        return StreamSupport.stream(handlers.spliterator(), false)
                .max(Comparator.comparingInt(RejectHandler::priority))
                .orElseGet(DefaultRejectHandler::new);
    }
}
