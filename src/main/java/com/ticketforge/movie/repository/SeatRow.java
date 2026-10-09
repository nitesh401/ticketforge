package com.ticketforge.movie.repository;

import com.ticketforge.movie.domain.SeatStatus;
import java.time.Instant;

/** Projection used to build the advisory seat map. */
public record SeatRow(String label, SeatStatus status, Instant holdExpiresAt) {
}
