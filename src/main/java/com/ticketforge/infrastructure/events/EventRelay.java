package com.ticketforge.infrastructure.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EventRelay {
    private static final Logger log = LoggerFactory.getLogger(EventRelay.class);
    private final EventSink sink;

    public EventRelay(EventSink sink) {
        this.sink = sink;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEvent(DomainEvent event) {
        try {
            sink.send(event);
        } catch (RuntimeException e) {
            // Booking state is already durable; a lost notification must not fail the request.
            log.error("Failed to publish event {} ({})", event.eventId(), event.type(), e);
        }
    }
}
