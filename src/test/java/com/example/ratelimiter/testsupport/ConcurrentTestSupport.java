package com.example.ratelimiter.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ConcurrentTestSupport {

    private ConcurrentTestSupport() {
    }

    public static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return task.call();
            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(5, TimeUnit.SECONDS));
        }
        executor.shutdownNow();
        return results;
    }
}
