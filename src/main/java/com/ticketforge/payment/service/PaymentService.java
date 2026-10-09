package com.ticketforge.payment.service;

import com.ticketforge.common.TicketMetrics;
import com.ticketforge.common.exception.ResourceNotFoundException;
import com.ticketforge.infrastructure.db.TransactionalRunner;
import com.ticketforge.infrastructure.events.DomainEvents;
import com.ticketforge.infrastructure.kafka.Topics;
import com.ticketforge.movie.domain.Booking;
import com.ticketforge.movie.domain.BookingStatus;
import com.ticketforge.movie.domain.HoldStatus;
import com.ticketforge.movie.domain.ShowSeat;
import com.ticketforge.movie.repository.BookingRepository;
import com.ticketforge.movie.repository.HoldItemRepository;
import com.ticketforge.movie.repository.HoldRepository;
import com.ticketforge.movie.repository.ShowSeatRepository;
import com.ticketforge.payment.domain.Payment;
import com.ticketforge.payment.domain.PaymentOutcome;
import com.ticketforge.payment.domain.PaymentStatus;
import com.ticketforge.payment.dto.PaymentCallbackRequest;
import com.ticketforge.payment.dto.PaymentCallbackResponse;
import com.ticketforge.payment.dto.ReconciliationItem;
import com.ticketforge.payment.repository.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles PSP callbacks. The rule: a successful payment is only turned into BOOKED seats if the hold
 * is still valid AND every seat still belongs to it. Otherwise the money is parked for refund
 * (REFUND_REQUIRED) and the seats, which may now belong to another customer, are left untouched.
 *
 * Lock order is always booking -> payment -> seats, matching cancelBooking, to avoid deadlocks.
 */
@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final HoldRepository holds;
    private final HoldItemRepository holdItems;
    private final ShowSeatRepository showSeats;
    private final TransactionalRunner tx;
    private final DomainEvents events;
    private final TicketMetrics metrics;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, BookingRepository bookings, HoldRepository holds,
                          HoldItemRepository holdItems, ShowSeatRepository showSeats, TransactionalRunner tx,
                          DomainEvents events, TicketMetrics metrics, Clock clock) {
        this.payments = payments;
        this.bookings = bookings;
        this.holds = holds;
        this.holdItems = holdItems;
        this.showSeats = showSeats;
        this.tx = tx;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
    }

    public PaymentCallbackResponse handleCallback(PaymentCallbackRequest request) {
        return tx.run(() -> process(request));
    }

    private PaymentCallbackResponse process(PaymentCallbackRequest request) {
        Payment unlocked = payments.findByProviderRef(request.paymentRef())
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Unknown payment reference"));
        Booking booking = bookings.findByIdForUpdate(unlocked.getBookingId()).orElseThrow();
        Payment payment = payments.findByProviderRefForUpdate(request.paymentRef()).orElseThrow();

        // Duplicate or late-duplicate callback: the first one already decided the outcome.
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return response(payment, booking, payment.getStatus() == PaymentStatus.REFUND_REQUIRED);
        }
        Instant now = clock.instant();
        return request.outcome() == PaymentOutcome.FAILURE
                ? onFailure(payment, booking, request.reason(), now)
                : onSuccess(payment, booking, now);
    }

    private PaymentCallbackResponse onFailure(Payment payment, Booking booking, String reason, Instant now) {
        payment.markFailed(reason == null ? "PAYMENT_DECLINED" : reason, now);
        if (booking.getStatus() == BookingStatus.PAYMENT_PENDING) {
            booking.transitionTo(BookingStatus.PAYMENT_FAILED, now);
            showSeats.releaseHeldBy(booking.getHoldId(), now);
            holds.findById(booking.getHoldId()).ifPresent(h -> h.setStatus(HoldStatus.RELEASED));
        }
        metrics.paymentFailure();
        events.publish(Topics.PAYMENT_EVENTS, "booking.payment.failed", booking.getPublicId(),
                Map.of("bookingId", booking.getPublicId(), "paymentRef", payment.getProviderRef()));
        return response(payment, booking, false);
    }

    private PaymentCallbackResponse onSuccess(Payment payment, Booking booking, Instant now) {
        if (booking.getStatus() != BookingStatus.PAYMENT_PENDING) {
            return parkForRefund(payment, booking, "BOOKING_" + booking.getStatus(), now);
        }
        // Lock the hold's seats (id order) and verify ownership + expiry in one consistent view.
        List<ShowSeat> seats = showSeats.lockByHoldOrdered(booking.getHoldId());
        long expected = holdItems.countByHoldId(booking.getHoldId());
        boolean intact = seats.size() == expected
                && seats.stream().allMatch(s -> s.isHeldBy(booking.getHoldId(), now));
        if (!intact) {
            // Hold lapsed (or seats were taken over): do NOT book. Seats may now belong to someone else.
            booking.transitionTo(BookingStatus.EXPIRED, now);
            holds.findById(booking.getHoldId()).ifPresent(h -> {
                if (h.getStatus() == HoldStatus.ACTIVE) {
                    h.setStatus(HoldStatus.EXPIRED);
                }
            });
            return parkForRefund(payment, booking, "HOLD_EXPIRED_BEFORE_PAYMENT", now);
        }
        seats.forEach(s -> s.markBooked(now));
        booking.transitionTo(BookingStatus.CONFIRMED, now);
        payment.markSucceeded(now);
        holds.findById(booking.getHoldId()).ifPresent(h -> h.setStatus(HoldStatus.CONFIRMED));
        metrics.bookingSuccess();
        events.publish(Topics.BOOKING_EVENTS, "booking.confirmed", booking.getPublicId(),
                Map.of("bookingId", booking.getPublicId(), "paymentRef", payment.getProviderRef()));
        return response(payment, booking, false);
    }

    private PaymentCallbackResponse parkForRefund(Payment payment, Booking booking, String reason, Instant now) {
        payment.markRefundRequired(reason, now);
        metrics.reconciliationRequired();
        events.publish(Topics.PAYMENT_EVENTS, "payment.refund.required", booking.getPublicId(),
                Map.of("bookingId", booking.getPublicId(), "paymentRef", payment.getProviderRef(), "reason", reason));
        return response(payment, booking, true);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationItem> pendingReconciliation() {
        return payments.findByStatus(PaymentStatus.REFUND_REQUIRED).stream().map(p -> {
            String bookingPublicId = bookings.findById(p.getBookingId()).map(Booking::getPublicId).orElse("?");
            return new ReconciliationItem(p.getProviderRef(), bookingPublicId, p.getAmountMinor(),
                    p.getCurrency().trim(), p.getReason());
        }).toList();
    }

    private static PaymentCallbackResponse response(Payment p, Booking b, boolean reconciliation) {
        return new PaymentCallbackResponse(p.getProviderRef(), p.getStatus().name(), b.getPublicId(),
                b.getStatus().name(), reconciliation);
    }
}
