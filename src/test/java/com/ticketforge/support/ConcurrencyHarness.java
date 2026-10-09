package com.ticketforge.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntPredicate;

/**
 * Releases N virtual threads at the same instant (start latch) and tallies outcomes.
 * The task returns true = succeeded, false = cleanly rejected; any exception counts as an error.
 */
public final class ConcurrencyHarness {

    public record Stats(String name, int total, int success, int rejected, int errors, Duration elapsed,
                        List<String> errorSamples) {
        public double throughputPerSecond() {
            return total / Math.max(0.001, elapsed.toNanos() / 1_000_000_000.0);
        }

        public double failureRate() {
            return total == 0 ? 0 : (double) (rejected + errors) / total;
        }

        @Override
        public String toString() {
            return String.format(
                    "[%s] requests=%d success=%d rejected=%d errors=%d time=%dms throughput=%.0f req/s failureRate=%.1f%%",
                    name, total, success, rejected, errors, elapsed.toMillis(), throughputPerSecond(), failureRate() * 100);
        }
    }

    private ConcurrencyHarness() {
    }

    public static Stats run(String name, int requests, IntPredicate task) {
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>(requests);
        long start;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < requests; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return task.test(index) ? 1 : 0;
                    } catch (Throwable t) {
                        errors.add(t.getClass().getSimpleName() + ": " + t.getMessage());
                        return -1;
                    }
                }));
            }
            try {
                ready.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            start = System.nanoTime();
            go.countDown();
            int success = 0;
            int rejected = 0;
            int failed = 0;
            for (Future<Integer> f : futures) {
                try {
                    int r = f.get();
                    if (r == 1) success++;
                    else if (r == 0) rejected++;
                    else failed++;
                } catch (Exception e) {
                    failed++;
                }
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            Stats stats = new Stats(name, requests, success, rejected, failed, elapsed,
                    errors.stream().limit(5).toList());
            System.out.println(stats);
            stats.errorSamples().forEach(s -> System.out.println("   error sample: " + s));
            return stats;
        }
    }
}
