package com.ticketforge.railway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ReservationRequest(
        @NotBlank String trainNo,
        @NotNull LocalDate journeyDate,
        @NotEmpty @Size(max = 6) List<@Valid PassengerRequest> passengers) {
}
