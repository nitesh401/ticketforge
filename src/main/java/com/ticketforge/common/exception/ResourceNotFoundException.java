package com.ticketforge.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends TicketForgeException {
    public ResourceNotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
