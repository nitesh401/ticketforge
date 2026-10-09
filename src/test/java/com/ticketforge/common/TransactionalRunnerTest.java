package com.ticketforge.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ticketforge.common.exception.SeatAlreadyHeldException;
import com.ticketforge.config.TicketForgeProperties;
import com.ticketforge.infrastructure.db.TransactionalRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class TransactionalRunnerTest {
    private TransactionalRunner runner;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var props = new TicketForgeProperties(
                new TicketForgeProperties.Hold(Duration.ofMinutes(5), "ATOMIC"),
                new TicketForgeProperties.Retry(3, Duration.ofMillis(1), Duration.ofMillis(4)),
                new TicketForgeProperties.Security("x".repeat(32), Duration.ofHours(1), false, "s"),
                new TicketForgeProperties.WaitingRoom(false, 1, Duration.ofMinutes(1), 10));
        runner = new TransactionalRunner(tm, props, new TicketMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void retriesTransientDeadlocksThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        String result = runner.run(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new DeadlockLoserDataAccessException("deadlock detected", null);
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void givesUpAfterBoundedAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> runner.run(() -> {
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("lock timeout");
        })).isInstanceOf(CannotAcquireLockException.class);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void businessConflictsAreNeverRetried() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> runner.run(() -> {
            attempts.incrementAndGet();
            throw new SeatAlreadyHeldException();
        })).isInstanceOf(SeatAlreadyHeldException.class);
        assertThat(attempts).hasValue(1);
    }
}
