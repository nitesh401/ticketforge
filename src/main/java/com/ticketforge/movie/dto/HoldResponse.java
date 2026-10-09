package com.ticketforge.movie.dto;

import java.time.Instant;
import java.util.List;

public record HoldResponse(String holdId, String status, Instant expiresAt, List<String> seatIds) {
}
