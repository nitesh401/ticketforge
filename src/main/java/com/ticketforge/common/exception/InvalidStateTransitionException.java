package com.ticketforge.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends TicketForgeException {
    public InvalidStateTransitionException(String from, String to) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STATE_TRANSITION",
                "Transition " + from + " -> " + to + " is not allowed");
    }
}
