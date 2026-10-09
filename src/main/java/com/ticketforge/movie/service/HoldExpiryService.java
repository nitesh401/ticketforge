package com.ticketforge.movie.service;

import com.ticketforge.common.TicketMetrics;
import com.ticketforge.infrastructure.db.TransactionalRunner;
import com.ticketforge.infrastructure.events.DomainEvents;
import com.ticketforge.infrastructure.kafka.Topics;
import com.ticketforge.movie.repository.ShowSeatRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Releases lapsed holds. Both statements are conditional on the CURRENT row state, so the sweeper
 * can never touch a BOOKED seat and cannot clobber a confirmation that raced it: whichever statement
 * reaches the row lock first wins, and the loser re-evaluates its WHERE clause against the new state.
 */
@Service
public class HoldExpiryService {
    private final ShowSeatRepository showSeats;
    private final JdbcTemplate jdbc;
    private final TransactionalRunner tx;
    private final DomainEvents events;
    private final TicketMetrics metrics;
    private final Clock clock;

    public HoldExpiryService(ShowSeatRepository showSeats, JdbcTemplate jdbc, TransactionalRunner tx,
                             DomainEvents events, TicketMetrics metrics, Clock clock) {
        this.showSeats = showSeats;
        this.jdbc = jdbc;
        this.tx = tx;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** @return number of seats returned to inventory */
    public int expireDueHolds() {
        Instant now = clock.instant();
        return tx.run(() -> {
            int seats = showSeats.releaseExpired(now);
            List<String> expired = jdbc.queryForList("""
                    UPDATE holds SET status = 'EXPIRED', version = version + 1
                     WHERE status = 'ACTIVE' AND expires_at < ?
                    RETURNING id
                    """, String.class, Timestamp.from(now));
            expired.forEach(id -> events.publish(Topics.SEAT_EVENTS, "seat.hold.expired", id, Map.of("holdId", id)));
            if (!expired.isEmpty()) {
                metrics.seatHoldExpired(expired.size());
            }
            return seats;
        });
    }
}
