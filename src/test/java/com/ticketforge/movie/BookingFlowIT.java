package com.ticketforge.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketforge.common.exception.BusinessRuleException;
import com.ticketforge.common.exception.InvalidStateTransitionException;
import com.ticketforge.idempotency.IdempotentResult;
import com.ticketforge.movie.domain.LockStrategy;
import com.ticketforge.movie.dto.BookingRequest;
import com.ticketforge.movie.dto.BookingResponse;
import com.ticketforge.movie.dto.HoldResponse;
import com.ticketforge.payment.domain.PaymentOutcome;
import com.ticketforge.payment.dto.PaymentCallbackResponse;
import com.ticketforge.support.AbstractPostgresIT;
import com.ticketforge.support.ConcurrencyHarness;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class BookingFlowIT extends AbstractPostgresIT {
    private static final LockStrategy S = LockStrategy.ATOMIC;

    @Test
    void duplicateBookingRequest_returnsOriginalAndCreatesOneBooking() {
        HoldResponse hold = hold("user-A", SHOW, S, "A1");
        BookingRequest request = new BookingRequest(hold.holdId());

        IdempotentResult<BookingResponse> first = bookingService.createBooking(request, user("user-A"), "key-1");
        IdempotentResult<BookingResponse> second = bookingService.createBooking(request, user("user-A"), "key-1");

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.body().bookingId()).isEqualTo(first.body().bookingId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Long.class)).isEqualTo(1L);
    }

    @Test
    void concurrentDuplicatesOfOneRequest_collapseIntoOneBooking() {
        HoldResponse hold = hold("user-A", SHOW, S, "A1");
        BookingRequest request = new BookingRequest(hold.holdId());
        Set<String> ids = ConcurrentHashMap.newKeySet();

        var stats = ConcurrencyHarness.run("20 identical retries", 20, i -> {
            ids.add(bookingService.createBooking(request, user("user-A"), "key-dup").body().bookingId());
            return true;
        });

        assertThat(stats.errors()).as("%s", stats.errorSamples()).isZero();
        assertThat(ids).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Long.class)).isEqualTo(1L);
    }

    @Test
    void sameKeyWithDifferentPayload_isRejected() {
        HoldResponse h1 = hold("user-A", SHOW, S, "A1");
        HoldResponse h2 = hold("user-A", SHOW, S, "A2");
        bookingService.createBooking(new BookingRequest(h1.holdId()), user("user-A"), "key-x");

        assertThatThrownBy(() -> bookingService.createBooking(new BookingRequest(h2.holdId()), user("user-A"), "key-x"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different request payload");
    }

    @Test
    void duplicatePaymentCallback_isHarmless() {
        HoldResponse hold = hold("user-A", SHOW, S, "A1");
        BookingResponse booking = book("user-A", hold.holdId());

        PaymentCallbackResponse first = pay(booking.paymentRef(), PaymentOutcome.SUCCESS);
        PaymentCallbackResponse second = pay(booking.paymentRef(), PaymentOutcome.SUCCESS);
        PaymentCallbackResponse contradicting = pay(booking.paymentRef(), PaymentOutcome.FAILURE);

        assertThat(first.bookingStatus()).isEqualTo("CONFIRMED");
        assertThat(second).isEqualTo(first);
        assertThat(contradicting.paymentStatus()).isEqualTo("SUCCEEDED");   // first outcome wins
        assertThat(seatStatus(SHOW, "A1")).isEqualTo("BOOKED");
    }

    @Test
    void failedPayment_releasesTheSeat() {
        HoldResponse hold = hold("user-A", SHOW, S, "A1");
        BookingResponse booking = book("user-A", hold.holdId());

        PaymentCallbackResponse result = pay(booking.paymentRef(), PaymentOutcome.FAILURE);

        assertThat(result.bookingStatus()).isEqualTo("PAYMENT_FAILED");
        assertThat(seatStatus(SHOW, "A1")).isEqualTo("AVAILABLE");
        assertThat(tryHold("user-B", SHOW, S, "A1")).isTrue();
    }

    @Test
    void cancelConfirmedBooking_returnsSeatAndRequestsRefund_secondCancelIsInvalid() {
        HoldResponse hold = hold("user-A", SHOW, S, "A1");
        BookingResponse booking = book("user-A", hold.holdId());
        pay(booking.paymentRef(), PaymentOutcome.SUCCESS);

        BookingResponse cancelled = bookingService.cancelBooking(booking.bookingId(), user("user-A"));

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.paymentStatus()).isEqualTo("REFUND_REQUIRED");
        assertThat(seatStatus(SHOW, "A1")).isEqualTo("AVAILABLE");
        assertThatThrownBy(() -> bookingService.cancelBooking(booking.bookingId(), user("user-A")))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void latePaymentAfterCancellation_isParkedForRefund() {
        HoldResponse hold = hold("user-A", SHOW, S, "A1");
        BookingResponse booking = book("user-A", hold.holdId());
        bookingService.cancelBooking(booking.bookingId(), user("user-A"));

        PaymentCallbackResponse late = pay(booking.paymentRef(), PaymentOutcome.SUCCESS);

        assertThat(late.reconciliationRequired()).isTrue();
        assertThat(seatStatus(SHOW, "A1")).isEqualTo("AVAILABLE");
    }
}
