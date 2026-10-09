package com.ticketforge.movie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record HoldRequest(
        @NotBlank String userId,
        @NotEmpty @Size(max = 10) List<@NotBlank String> seatIds) {
}
