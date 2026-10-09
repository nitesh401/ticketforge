package com.ticketforge.movie.dto;

import java.util.List;

public record SeatMapResponse(String showId, List<SeatView> seats, String note) {
}
