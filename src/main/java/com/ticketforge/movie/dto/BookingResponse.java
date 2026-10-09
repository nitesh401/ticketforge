package com.ticketforge.movie.dto;

import java.util.List;

public record BookingResponse(String bookingId, String status, String holdId, String showId, List<String> seatIds,
                              long totalAmountMinor, String currency, String paymentRef, String paymentStatus) {
}
