package com.ticketforge.railway.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PassengerRequest(@NotBlank @Size(max = 120) String name, @Min(0) @Max(120) int age) {
}
