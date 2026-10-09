package com.ticketforge.movie.dto;

import com.ticketforge.movie.domain.SeatStatus;

public record SeatView(String seatId, SeatStatus status) {
}
