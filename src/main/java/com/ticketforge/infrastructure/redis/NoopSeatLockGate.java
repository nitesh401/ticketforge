package com.ticketforge.infrastructure.redis;

import java.util.Collection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ticketforge.redis.enabled", havingValue = "false", matchIfMissing = true)
public class NoopSeatLockGate implements SeatLockGate {
    @Override
    public boolean tryLock(long showId, Collection<String> seatLabels, String holdId) {
        return true;
    }

    @Override
    public void release(long showId, Collection<String> seatLabels, String holdId) {
        // nothing to release
    }
}
