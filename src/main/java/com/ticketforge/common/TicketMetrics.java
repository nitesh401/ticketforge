package com.ticketforge.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Dotted names; the Prometheus registry renders them as booking_attempts_total etc. */
@Component
public class TicketMetrics {
    private final Counter bookingAttempts;
    private final Counter bookingSuccess;
    private final Counter bookingConflicts;
    private final Counter seatHoldCreated;
    private final Counter seatHoldExpired;
    private final Counter paymentFailures;
    private final Counter inventorySuccess;
    private final Counter inventoryConflict;
    private final Counter dbRetries;
    private final Counter reconciliations;
    private final Counter idempotentReplays;

    public TicketMetrics(MeterRegistry r) {
        bookingAttempts = Counter.builder("booking.attempts").description("Seat hold / booking attempts").register(r);
        bookingSuccess = Counter.builder("booking.success").description("Confirmed bookings").register(r);
        bookingConflicts = Counter.builder("booking.conflicts").description("Seat conflicts").register(r);
        seatHoldCreated = Counter.builder("seat.hold.created").description("Holds created").register(r);
        seatHoldExpired = Counter.builder("seat.hold.expired").description("Holds expired by sweeper").register(r);
        paymentFailures = Counter.builder("payment.failures").description("Failed payments").register(r);
        inventorySuccess = Counter.builder("inventory.allocation.success").description("Tatkal allocations").register(r);
        inventoryConflict = Counter.builder("inventory.allocation.conflict").description("Tatkal rejections").register(r);
        dbRetries = Counter.builder("db.transient.retries").description("Retries after deadlock/lock failure").register(r);
        reconciliations = Counter.builder("payment.reconciliation.required").description("Late payments needing refund").register(r);
        idempotentReplays = Counter.builder("idempotency.replays").description("Replayed idempotent responses").register(r);
    }

    public void bookingAttempt() { bookingAttempts.increment(); }
    public void bookingSuccess() { bookingSuccess.increment(); }
    public void bookingConflict() { bookingConflicts.increment(); }
    public void seatHoldCreated() { seatHoldCreated.increment(); }
    public void seatHoldExpired(int n) { seatHoldExpired.increment(n); }
    public void paymentFailure() { paymentFailures.increment(); }
    public void inventorySuccess() { inventorySuccess.increment(); }
    public void inventoryConflict() { inventoryConflict.increment(); }
    public void dbRetry() { dbRetries.increment(); }
    public void reconciliationRequired() { reconciliations.increment(); }
    public void idempotentReplay() { idempotentReplays.increment(); }
}
