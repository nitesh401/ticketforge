package com.ticketforge.movie.dto;

import java.util.List;

public record MovieView(String title, int durationMinutes, List<ShowView> shows) {
}
