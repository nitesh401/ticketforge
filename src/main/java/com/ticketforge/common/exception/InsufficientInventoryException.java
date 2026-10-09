package com.ticketforge.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientInventoryException extends TicketForgeException {
    public InsufficientInventoryException() {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_INVENTORY", "Not enough inventory left in this quota");
    }
}
