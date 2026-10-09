package com.ticketforge.common.exception;

import org.springframework.http.HttpStatus;

/** Base type for all expected, client-facing failures. Never retried by the DB retry loop. */
public class TicketForgeException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public TicketForgeException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
