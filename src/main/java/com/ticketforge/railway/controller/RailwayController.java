package com.ticketforge.railway.controller;

import com.ticketforge.idempotency.IdempotentResult;
import com.ticketforge.railway.dto.AvailabilityResponse;
import com.ticketforge.railway.dto.ReservationRequest;
import com.ticketforge.railway.dto.ReservationResponse;
import com.ticketforge.railway.service.RailReservationService;
import com.ticketforge.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RailwayController {
    private final RailReservationService service;

    public RailwayController(RailReservationService service) {
        this.service = service;
    }

    @GetMapping("/trains/{trainNo}/availability")
    public AvailabilityResponse availability(
            @PathVariable String trainNo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate) {
        return service.availability(trainNo, journeyDate);
    }

    @PostMapping("/tatkal/reservations")
    public ResponseEntity<ReservationResponse> reserve(
            @Valid @RequestBody ReservationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Admission-Token", required = false) String admissionToken) {
        IdempotentResult<ReservationResponse> result =
                service.reserve(request, CurrentUser.require(), idempotencyKey, admissionToken);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.body());
    }

    @GetMapping("/tatkal/reservations/{id}")
    public ReservationResponse get(@PathVariable String id) {
        return service.getReservation(id, CurrentUser.require());
    }
}
