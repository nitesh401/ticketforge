package com.ticketforge.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenOperationException extends TicketForgeException {
    public ForbiddenOperationException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
