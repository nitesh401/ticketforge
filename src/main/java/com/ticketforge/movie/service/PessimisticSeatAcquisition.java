package com.ticketforge.movie.service;

import com.ticketforge.common.exception.ResourceNotFoundException;
import com.ticketforge.common.exception.SeatAlreadyHeldException;
import com.ticketforge.movie.domain.LockStrategy;
import com.ticketforge.movie.domain.ShowSeat;
import com.ticketforge.movie.repository.ShowSeatRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * SELECT ... FOR UPDATE on all requested rows in id order, check, then mutate.
 * A competing transaction blocks on the first row lock until we commit or roll back, then re-reads
 * the committed state and sees HELD. Cost: waiters queue behind the lock holder.
 */
@Component
public class PessimisticSeatAcquisition implements SeatAcquisitionStrategy {
    private final ShowSeatRepository showSeats;

    public PessimisticSeatAcquisition(ShowSeatRepository showSeats) {
        this.showSeats = showSeats;
    }

    @Override
    public LockStrategy type() {
        return LockStrategy.PESSIMISTIC;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(List<Long> orderedShowSeatIds, String holdId, Instant expiresAt, Instant now) {
        List<ShowSeat> locked = showSeats.lockAllOrdered(orderedShowSeatIds);
        if (locked.size() != orderedShowSeatIds.size()) {
            throw new ResourceNotFoundException("SEAT_NOT_FOUND", "One or more seats do not exist for this show");
        }
        for (ShowSeat seat : locked) {
            if (!seat.isAcquirableAt(now)) {
                throw new SeatAlreadyHeldException();
            }
        }
        locked.forEach(seat -> seat.hold(holdId, expiresAt, now));
    }
}
