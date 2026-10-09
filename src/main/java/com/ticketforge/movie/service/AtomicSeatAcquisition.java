package com.ticketforge.movie.service;

import com.ticketforge.common.exception.SeatAlreadyHeldException;
import com.ticketforge.movie.domain.LockStrategy;
import com.ticketforge.movie.repository.ShowSeatRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * UPDATE show_seats SET status='HELD' ... WHERE id = ? AND status = 'AVAILABLE'
 *
 * Check and write are ONE statement, so there is no gap between "read AVAILABLE" and "write HELD" for
 * another transaction to slip into. rowsUpdated == 1 means we won the seat; 0 means someone else did.
 * Ids arrive sorted, so concurrent multi-seat requests lock rows in the same order and cannot deadlock.
 */
@Component
public class AtomicSeatAcquisition implements SeatAcquisitionStrategy {
    private final ShowSeatRepository showSeats;

    public AtomicSeatAcquisition(ShowSeatRepository showSeats) {
        this.showSeats = showSeats;
    }

    @Override
    public LockStrategy type() {
        return LockStrategy.ATOMIC;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(List<Long> orderedShowSeatIds, String holdId, Instant expiresAt, Instant now) {
        for (Long seatId : orderedShowSeatIds) {
            if (!showSeats.tryAcquire(seatId, holdId, expiresAt, now)) {
                // Throwing here rolls back seats already acquired in this loop: no partial reservations.
                throw new SeatAlreadyHeldException();
            }
        }
    }
}
