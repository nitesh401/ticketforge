package com.ticketforge.common.exception;

import org.springframework.http.HttpStatus;

/** 422: the request is well formed but violates a business rule. */
public class BusinessRuleException extends TicketForgeException {
    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
