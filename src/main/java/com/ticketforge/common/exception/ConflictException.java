package com.ticketforge.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends TicketForgeException {
    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
