package com.ticketforge.idempotency;

public record IdempotentResult<T>(T body, boolean replayed) {
}
