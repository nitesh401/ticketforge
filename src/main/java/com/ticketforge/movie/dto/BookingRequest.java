package com.ticketforge.movie.dto;

import jakarta.validation.constraints.NotBlank;

public record BookingRequest(@NotBlank String holdId) {
}
