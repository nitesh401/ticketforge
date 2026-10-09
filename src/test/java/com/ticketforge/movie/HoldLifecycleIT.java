package com.ticketforge.movie;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketforge.movie.domain.LockStrategy;
import com.ticketforge.movie.dto.BookingResponse;
import com.ticketforge.movie.dto.HoldResponse;
import com.ticketforge.payment.domain.PaymentOutcome;
import com.ticketforge.payment.dto.PaymentCallbackResponse;
import com.ticketforge.support.AbstractPostgresIT;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class HoldLifecycleIT extends AbstractPostgresIT {
    private static final LockStrategy S = LockStrategy.ATOMIC;

    @Test
    void holdExpires_seatReturnsToInventory_andAnotherUserCanTakeIt() {
        HoldResponse a = hold("user-A", SHOW, S, "A1");
        assertThat(tryHold("user-B", SHOW, S, "A1")).isFalse();

        clock.advance(Duration.ofMinutes(6));
        assertThat(expiryService.expireDueHolds()).isEqualTo(1);

        assertThat(seatStatus(SHOW, "A1")).isEqualTo("AVAILABLE");
        assertThat(holdStatus(a.holdId())).isEqualTo("EXPIRED");
        assertThat(tryHold("user-B", SHOW, S, "A1")).isTrue();
    }

    @Test
    void lapsedHoldCanBeTakenOverEvenBeforeTheSweeperRuns() {
        hold("user-A", SHOW, S, "A1");
        clock.advance(Duration.ofMinutes(6));

        assertThat(tryHold("user-B", SHOW, LockStrategy.PESSIMISTIC, "A1")).isTrue();
    }

    @Test
    void sweeperNeverReleasesBookedSeats() {
        HoldResponse a = hold("user-A", SHOW, S, "A1");
        BookingResponse booking = book("user-A", a.holdId());
        pay(booking.paymentRef(), PaymentOutcome.SUCCESS);

        clock.advance(Duration.ofHours(1));
        expiryService.expireDueHolds();

        assertThat(seatStatus(SHOW, "A1")).isEqualTo("BOOKED");
        assertThat(holdStatus(a.holdId())).isEqualTo("CONFIRMED");
    }

    @Test
    void paymentBeforeExpiry_confirmsBooking() {
        HoldResponse a = hold("user-A", SHOW, S, "A1", "A2");
        BookingResponse booking = book("user-A", a.holdId());

        PaymentCallbackResponse result = pay(booking.paymentRef(), PaymentOutcome.SUCCESS);

        assertThat(result.bookingStatus()).isEqualTo("CONFIRMED");
        assertThat(result.reconciliationRequired()).isFalse();
        assertThat(seatStatus(SHOW, "A1")).isEqualTo("BOOKED");
        assertThat(seatStatus(SHOW, "A2")).isEqualTo("BOOKED");
    }

    @Test
    void paymentAfterExpiry_isParkedForRefund_andSeatsAreNotBooked() {
        HoldResponse a = hold("user-A", SHOW, S, "A1");
        BookingResponse booking = book("user-A", a.holdId());
        clock.advance(Duration.ofMinutes(6));

        PaymentCallbackResponse result = pay(booking.paymentRef(), PaymentOutcome.SUCCESS);

        assertThat(result.reconciliationRequired()).isTrue();
        assertThat(result.paymentStatus()).isEqualTo("REFUND_REQUIRED");
        assertThat(result.bookingStatus()).isEqualTo("EXPIRED");
        assertThat(seatStatus(SHOW, "A1")).isNotEqualTo("BOOKED");
        assertThat(paymentService.pendingReconciliation()).hasSize(1);
    }

    @Test
    void latePaymentFromUserA_neverOverwritesUserBsBooking() {
        HoldResponse a = hold("user-A", SHOW, S, "A1");
        BookingResponse bookingA = book("user-A", a.holdId());

        clock.advance(Duration.ofMinutes(6));                       // A's hold lapses, A is still "paying"
        HoldResponse b = hold("user-B", SHOW, S, "A1");             // B takes the seat
        BookingResponse bookingB = book("user-B", b.holdId());
        assertThat(pay(bookingB.paymentRef(), PaymentOutcome.SUCCESS).bookingStatus()).isEqualTo("CONFIRMED");

        PaymentCallbackResponse late = pay(bookingA.paymentRef(), PaymentOutcome.SUCCESS);  // A's PSP finally calls back

        assertThat(late.reconciliationRequired()).isTrue();
        assertThat(late.bookingStatus()).isEqualTo("EXPIRED");
        assertThat(seatStatus(SHOW, "A1")).isEqualTo("BOOKED");
        assertThat(seatHoldId(SHOW, "A1")).isEqualTo(b.holdId());
    }
}
