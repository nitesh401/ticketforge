package com.ticketforge.waitingroom;

import com.ticketforge.common.exception.ForbiddenOperationException;
import com.ticketforge.common.exception.ResourceNotFoundException;
import com.ticketforge.common.exception.TicketForgeException;
import com.ticketforge.config.TicketForgeProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Simplified virtual waiting room. Clients take a ticket, wait in FIFO order, and are admitted at a
 * controlled rate, so the booking database sees a steady trickle instead of a 50,000-request spike.
 *
 * Single-JVM and in-memory on purpose: it shows the mechanism. A production version keeps the queue in
 * Redis (sorted set / stream) so every instance shares one ordering, and signs the admission token.
 */
@Service
public class WaitingRoomService {

    public enum State { WAITING, ADMITTED, EXPIRED }

    public record Ticket(String ticketId, String userId, long sequence, Instant admittedAt) {
        Ticket admit(Instant now) {
            return new Ticket(ticketId, userId, sequence, now);
        }
    }

    public record Status(String ticketId, State state, long position, String admissionToken) {
    }

    private final TicketForgeProperties.WaitingRoom config;
    private final Clock clock;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final AtomicLong nextSequence = new AtomicLong();
    private final AtomicLong lastAdmittedSequence = new AtomicLong();

    public WaitingRoomService(TicketForgeProperties props, Clock clock) {
        this.config = props.waitingRoom();
        this.clock = clock;
    }

    public boolean isEnabled() {
        return config.enabled();
    }

    public Status join(String userId) {
        if (queue.size() >= config.maxQueueSize()) {
            throw new TicketForgeException(HttpStatus.TOO_MANY_REQUESTS, "WAITING_ROOM_FULL", "Waiting room is full, try later");
        }
        String id = UUID.randomUUID().toString();
        Ticket ticket = new Ticket(id, userId, nextSequence.incrementAndGet(), null);
        tickets.put(id, ticket);
        queue.add(id);
        return status(id, userId);
    }

    public Status status(String ticketId, String userId) {
        Ticket t = tickets.get(ticketId);
        if (t == null || !t.userId().equals(userId)) {
            throw new ResourceNotFoundException("TICKET_NOT_FOUND", "Unknown waiting room ticket");
        }
        if (t.admittedAt() == null) {
            return new Status(ticketId, State.WAITING, t.sequence() - lastAdmittedSequence.get(), null);
        }
        boolean live = isLive(t, clock.instant());
        return new Status(ticketId, live ? State.ADMITTED : State.EXPIRED, 0, live ? ticketId : null);
    }

    /** Admits up to admitPerSecond waiting tickets each second. Visible for tests. */
    @Scheduled(fixedRate = 1000)
    public void admitNext() {
        if (!config.enabled()) {
            return;
        }
        admit(config.admitPerSecond());
    }

    public int admit(int max) {
        Instant now = clock.instant();
        int admitted = 0;
        String id;
        while (admitted < max && (id = queue.poll()) != null) {
            Ticket t = tickets.get(id);
            if (t != null) {
                tickets.put(id, t.admit(now));
                lastAdmittedSequence.accumulateAndGet(t.sequence(), Math::max);
                admitted++;
            }
        }
        purgeExpired(now);
        return admitted;
    }

    /** Called by the reservation endpoint when the waiting room is enabled. */
    public void requireAdmission(String userId, String token) {
        if (!config.enabled()) {
            return;
        }
        Ticket t = token == null ? null : tickets.get(token);
        if (t == null || !t.userId().equals(userId) || t.admittedAt() == null || !isLive(t, clock.instant())) {
            throw new ForbiddenOperationException("NOT_ADMITTED",
                    "Join the waiting room and wait for admission before booking");
        }
    }

    private boolean isLive(Ticket t, Instant now) {
        Duration ttl = config.admissionTtl();
        return t.admittedAt() != null && t.admittedAt().plus(ttl).isAfter(now);
    }

    private void purgeExpired(Instant now) {
        tickets.values().removeIf(t -> t.admittedAt() != null && !isLive(t, now));
    }
}
