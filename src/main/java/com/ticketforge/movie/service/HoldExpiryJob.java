package com.ticketforge.movie.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safe to run on every instance at once: the release statements are idempotent and state-conditional.
 * Hold validity never depends on this job running, because acquisition and confirmation compare
 * hold_expires_at against the clock themselves; the sweeper only keeps the table tidy and emits events.
 */
@Component
@ConditionalOnProperty(name = "ticketforge.hold.sweeper-enabled", havingValue = "true", matchIfMissing = true)
public class HoldExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);
    private final HoldExpiryService service;

    public HoldExpiryJob(HoldExpiryService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${ticketforge.hold.sweep-interval-ms:1000}")
    public void sweep() {
        try {
            int released = service.expireDueHolds();
            if (released > 0) {
                log.info("Released {} expired seat holds", released);
            }
        } catch (RuntimeException e) {
            log.warn("Hold sweep failed, will retry on next tick", e);
        }
    }
}
