package com.ticketforge.railway.dto;

import java.time.LocalDate;
import java.util.List;

public record ReservationResponse(String reservationId, String pnr, String trainNo, LocalDate journeyDate,
                                  String quota, String status, List<PassengerAllocation> passengers) {
}
