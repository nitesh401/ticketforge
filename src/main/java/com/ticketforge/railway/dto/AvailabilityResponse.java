package com.ticketforge.railway.dto;

import java.time.LocalDate;

public record AvailabilityResponse(String trainNo, String trainName, LocalDate journeyDate, String quota,
                                   int totalCapacity, int availableCount, String note) {
}
