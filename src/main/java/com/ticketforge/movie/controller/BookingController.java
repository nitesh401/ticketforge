package com.ticketforge.movie.controller;

import com.ticketforge.idempotency.IdempotentResult;
import com.ticketforge.movie.dto.BookingRequest;
import com.ticketforge.movie.dto.BookingResponse;
import com.ticketforge.movie.service.BookingService;
import com.ticketforge.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IdempotentResult<BookingResponse> result =
                bookingService.createBooking(request, CurrentUser.require(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.body());
    }

    @GetMapping("/{bookingId}")
    public BookingResponse get(@PathVariable String bookingId) {
        return bookingService.getBooking(bookingId, CurrentUser.require());
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancel(@PathVariable String bookingId) {
        return bookingService.cancelBooking(bookingId, CurrentUser.require());
    }
}
