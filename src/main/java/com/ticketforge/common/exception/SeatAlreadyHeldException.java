package com.ticketforge.common.exception;

import org.springframework.http.HttpStatus;

public class SeatAlreadyHeldException extends TicketForgeException {
    public SeatAlreadyHeldException() {
        super(HttpStatus.CONFLICT, "SEAT_ALREADY_HELD", "One or more selected seats are no longer available");
    }
}
