package com.ticketforge.infrastructure.db;

import com.ticketforge.common.TicketMetrics;
import com.ticketforge.config.TicketForgeProperties;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns transaction boundaries AND the retry loop around them. The retry must sit outside the
 * transaction: a deadlock victim's transaction is already rolled back and cannot be resumed.
 *
 * Only transient database failures are retried (deadlock, lock timeout, serialization failure).
 * Business outcomes such as SEAT_ALREADY_HELD are TicketForgeExceptions and propagate untouched,
 * because retrying a conflict can never turn it into a success.
 */
@Component
public class TransactionalRunner {
    private static final Logger log = LoggerFactory.getLogger(TransactionalRunner.class);

    private final PlatformTransactionManager txManager;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final TicketMetrics metrics;

    public TransactionalRunner(PlatformTransactionManager txManager, TicketForgeProperties props, TicketMetrics metrics) {
        this.txManager = txManager;
        this.maxAttempts = props.retry().maxAttempts();
        this.initialBackoff = props.retry().initialBackoff();
        this.maxBackoff = props.retry().maxBackoff();
        this.metrics = metrics;
    }

    /** Default isolation (READ COMMITTED on PostgreSQL). */
    public <T> T run(Supplier<T> work) {
        return run(TransactionDefinitionIsolation.DEFAULT, work);
    }

    public <T> T run(int isolationLevel, Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setIsolationLevel(isolationLevel);
        long backoffMillis = initialBackoff.toMillis();
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return template.execute(status -> work.get());
            } catch (TransientDataAccessException e) {
                if (attempt >= maxAttempts) {
                    log.warn("Giving up after {} attempts: {}", attempt, e.getClass().getSimpleName());
                    throw e;
                }
                metrics.dbRetry();
                sleepWithJitter(backoffMillis);
                backoffMillis = Math.min(backoffMillis * 2, maxBackoff.toMillis());
            }
        }
    }

    private static void sleepWithJitter(long backoffMillis) {
        long delay = ThreadLocalRandom.current().nextLong(Math.max(1, backoffMillis / 2), Math.max(2, backoffMillis + 1));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off", ie);
        }
    }
}
