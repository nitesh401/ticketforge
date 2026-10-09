package com.ticketforge.movie.dto;

import java.time.Instant;

public record ShowView(String showId, String theatre, String screen, Instant startsAt, long priceMinor, String currency) {
}
