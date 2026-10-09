package com.ticketforge.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketforge.common.exception.InvalidStateTransitionException;
import com.ticketforge.movie.domain.Booking;
import com.ticketforge.movie.domain.BookingStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookingStatusTest {
    private final Instant now = Instant.parse("2026-10-09T10:00:00Z");

    private Booking booking() {
        return new Booking("BKG-1", "user-1", 1L, "HOLD-1", 1000, "INR", now);
    }

    @Test
    void happyPath() {
        Booking b = booking();
        b.transitionTo(BookingStatus.PAYMENT_PENDING, now);
        b.transitionTo(BookingStatus.CONFIRMED, now);
        b.transitionTo(BookingStatus.CANCELLED, now);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void confirmedCannotGoBackToPaymentPending() {
        Booking b = booking();
        b.transitionTo(BookingStatus.PAYMENT_PENDING, now);
        b.transitionTo(BookingStatus.CONFIRMED, now);
        assertThatThrownBy(() -> b.transitionTo(BookingStatus.PAYMENT_PENDING, now))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void initiatedCannotJumpToConfirmed() {
        assertThatThrownBy(() -> booking().transitionTo(BookingStatus.CONFIRMED, now))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void terminalStatesHaveNoExits() {
        for (BookingStatus s : new BookingStatus[]{BookingStatus.PAYMENT_FAILED, BookingStatus.CANCELLED, BookingStatus.EXPIRED}) {
            assertThat(s.allowedNext()).isEmpty();
        }
    }
}
