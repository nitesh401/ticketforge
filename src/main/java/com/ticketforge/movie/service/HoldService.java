package com.ticketforge.movie.service;

import com.ticketforge.common.Ids;
import com.ticketforge.common.TicketMetrics;
import com.ticketforge.common.exception.ForbiddenOperationException;
import com.ticketforge.common.exception.ResourceNotFoundException;
import com.ticketforge.common.exception.SeatAlreadyHeldException;
import com.ticketforge.config.TicketForgeProperties;
import com.ticketforge.idempotency.IdempotencyService;
import com.ticketforge.idempotency.IdempotentResult;
import com.ticketforge.infrastructure.db.TransactionalRunner;
import com.ticketforge.infrastructure.events.DomainEvents;
import com.ticketforge.infrastructure.kafka.Topics;
import com.ticketforge.infrastructure.redis.SeatLockGate;
import com.ticketforge.movie.domain.Hold;
import com.ticketforge.movie.domain.HoldItem;
import com.ticketforge.movie.domain.LockStrategy;
import com.ticketforge.movie.domain.Show;
import com.ticketforge.movie.dto.HoldRequest;
import com.ticketforge.movie.dto.HoldResponse;
import com.ticketforge.movie.repository.HoldItemRepository;
import com.ticketforge.movie.repository.HoldRepository;
import com.ticketforge.movie.repository.ShowRepository;
import com.ticketforge.movie.repository.ShowSeatRepository;
import com.ticketforge.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class HoldService {
    private final ShowRepository shows;
    private final ShowSeatRepository showSeats;
    private final HoldRepository holds;
    private final HoldItemRepository holdItems;
    private final Map<LockStrategy, SeatAcquisitionStrategy> strategies = new EnumMap<>(LockStrategy.class);
    private final LockStrategy defaultStrategy;
    private final TransactionalRunner tx;
    private final IdempotencyService idempotency;
    private final SeatLockGate lockGate;
    private final DomainEvents events;
    private final TicketMetrics metrics;
    private final TicketForgeProperties props;
    private final Clock clock;

    public HoldService(ShowRepository shows, ShowSeatRepository showSeats, HoldRepository holds,
                       HoldItemRepository holdItems, List<SeatAcquisitionStrategy> strategyBeans,
                       TransactionalRunner tx, IdempotencyService idempotency, SeatLockGate lockGate,
                       DomainEvents events, TicketMetrics metrics, TicketForgeProperties props, Clock clock) {
        this.shows = shows;
        this.showSeats = showSeats;
        this.holds = holds;
        this.holdItems = holdItems;
        strategyBeans.forEach(s -> strategies.put(s.type(), s));
        this.defaultStrategy = LockStrategy.valueOf(props.hold().defaultStrategy());
        this.tx = tx;
        this.idempotency = idempotency;
        this.lockGate = lockGate;
        this.events = events;
        this.metrics = metrics;
        this.props = props;
        this.clock = clock;
    }

    public IdempotentResult<HoldResponse> createHold(String showPublicId, HoldRequest request, AuthenticatedUser user,
                                                     String idempotencyKey, LockStrategy requestedStrategy) {
        if (!user.isAdmin() && !user.userId().equals(request.userId())) {
            throw new ForbiddenOperationException("HOLD_OWNER_MISMATCH", "userId does not match the authenticated user");
        }
        metrics.bookingAttempt();
        Show show = shows.findByPublicId(showPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("SHOW_NOT_FOUND", "Show not found"));
        // Normalised and sorted so every caller derives the same order from the same seat set.
        List<String> labels = request.seatIds().stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT)).distinct().sorted().toList();
        SeatAcquisitionStrategy strategy = strategies.get(requestedStrategy != null ? requestedStrategy : defaultStrategy);
        String holdId = Ids.hold();

        // Optional Redis fast-path: only ever rejects early. The database below stays authoritative.
        if (!lockGate.tryLock(show.getId(), labels, holdId)) {
            metrics.bookingConflict();
            throw new SeatAlreadyHeldException();
        }
        try {
            Supplier<HoldResponse> action = () -> createInTransaction(show, labels, request.userId(), holdId, strategy);
            IdempotentResult<HoldResponse> result = idempotencyKey == null
                    ? new IdempotentResult<>(tx.run(action), false)
                    : idempotency.execute(user.userId(), idempotencyKey, request, HoldResponse.class, 201, action);
            if (result.replayed()) {
                lockGate.release(show.getId(), labels, holdId);
            } else {
                metrics.seatHoldCreated();
            }
            return result;
        } catch (RuntimeException e) {
            lockGate.release(show.getId(), labels, holdId);
            if (e instanceof SeatAlreadyHeldException) {
                metrics.bookingConflict();
            }
            throw e;
        }
    }

    /** Runs inside the caller's transaction (see TransactionalRunner). All-or-nothing for the whole seat set. */
    private HoldResponse createInTransaction(Show show, List<String> labels, String userId, String holdId,
                                             SeatAcquisitionStrategy strategy) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(props.hold().duration());

        List<Long> seatIds = showSeats.findIdsByShowAndLabels(show.getId(), labels).stream().sorted().toList();
        if (seatIds.size() != labels.size()) {
            throw new ResourceNotFoundException("SEAT_NOT_FOUND", "One or more seats do not exist for this show");
        }
        holds.save(new Hold(holdId, userId, show.getId(), expiresAt, now));
        strategy.acquire(seatIds, holdId, expiresAt, now);
        holdItems.saveAll(seatIds.stream().map(id -> new HoldItem(holdId, id)).toList());

        events.publish(Topics.SEAT_EVENTS, "seat.hold.created", holdId,
                Map.of("holdId", holdId, "showId", show.getPublicId(), "seats", labels));
        return new HoldResponse(holdId, "HELD", expiresAt, labels);
    }

    public HoldResponse getHold(String holdId, AuthenticatedUser user) {
        Hold hold = holds.findById(holdId)
                .filter(h -> user.isAdmin() || h.getUserId().equals(user.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("HOLD_NOT_FOUND", "Hold not found"));
        String status = hold.effectiveStatus(clock.instant()).name();
        return new HoldResponse(hold.getId(), "ACTIVE".equals(status) ? "HELD" : status, hold.getExpiresAt(),
                holdItems.findSeatLabels(hold.getId()));
    }
}
