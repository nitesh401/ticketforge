package com.ticketforge.movie.service;

import com.ticketforge.movie.domain.LockStrategy;
import java.time.Instant;
import java.util.List;

public interface SeatAcquisitionStrategy {

    LockStrategy type();

    /**
     * Acquires every seat or none. Callers pass ids already sorted ascending and run inside one
     * transaction, so throwing rolls back any seats acquired earlier in the loop.
     *
     * @throws com.ticketforge.common.exception.SeatAlreadyHeldException if any seat is not acquirable
     */
    void acquire(List<Long> orderedShowSeatIds, String holdId, Instant expiresAt, Instant now);
}
