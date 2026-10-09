package com.ticketforge.infrastructure.redis;

import java.util.Collection;

/**
 * Optional fast-path in front of the database. It only ever rejects early; it never grants
 * ownership. The database transition remains the sole authority for seat state.
 */
public interface SeatLockGate {
    boolean tryLock(long showId, Collection<String> seatLabels, String holdId);

    void release(long showId, Collection<String> seatLabels, String holdId);
}
