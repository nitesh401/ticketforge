package com.ticketforge.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketforge.support.ConcurrencyHarness;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * No database needed. Shows the check-then-act bug in plain Java, and the fix inside ONE JVM.
 * The in-JVM fix says nothing about 20 instances behind a load balancer: that is why the real
 * implementation moves the atomic step into PostgreSQL.
 */
class InMemoryRaceDemoTest {

    static final class BrokenInventory {
        private volatile int available = 100;

        boolean tryTake() {
            if (available > 0) {           // check
                pause();                   // another thread reads the same value here
                available = available - 1; // act on stale data
                return true;
            }
            return false;
        }
    }

    static final class AtomicInventory {
        private final AtomicInteger available = new AtomicInteger(100);

        boolean tryTake() {
            while (true) {
                int current = available.get();
                if (current <= 0) {
                    return false;
                }
                if (available.compareAndSet(current, current - 1)) {   // check + write as one step
                    return true;
                }
            }
        }
    }

    private static void pause() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void checkThenAct_oversells() {
        BrokenInventory inventory = new BrokenInventory();
        var stats = ConcurrencyHarness.run("in-memory check-then-act", 1000, i -> inventory.tryTake());
        assertThat(stats.success()).isGreaterThan(100);
    }

    @Test
    void atomicCompareAndSet_neverOversells() {
        AtomicInventory inventory = new AtomicInventory();
        var stats = ConcurrencyHarness.run("in-memory compare-and-set", 1000, i -> inventory.tryTake());
        assertThat(stats.success()).isEqualTo(100);
    }
}
