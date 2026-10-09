package com.ticketforge.movie.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * INITIATED ---> PAYMENT_PENDING ---> CONFIRMED ---> CANCELLED
 *     |               |  \--> PAYMENT_FAILED
 *     |               \-----> EXPIRED / CANCELLED
 *     \---> CANCELLED / EXPIRED
 * PAYMENT_FAILED, CANCELLED and EXPIRED are terminal.
 */
public enum BookingStatus {
    INITIATED, PAYMENT_PENDING, CONFIRMED, PAYMENT_FAILED, CANCELLED, EXPIRED;

    public Set<BookingStatus> allowedNext() {
        return switch (this) {
            case INITIATED -> EnumSet.of(PAYMENT_PENDING, CANCELLED, EXPIRED);
            case PAYMENT_PENDING -> EnumSet.of(CONFIRMED, PAYMENT_FAILED, CANCELLED, EXPIRED);
            case CONFIRMED -> EnumSet.of(CANCELLED);
            case PAYMENT_FAILED, CANCELLED, EXPIRED -> EnumSet.noneOf(BookingStatus.class);
        };
    }

    public boolean canTransitionTo(BookingStatus next) {
        return allowedNext().contains(next);
    }
}
