package com.ticketforge.infrastructure.events;

public interface EventSink {
    void send(DomainEvent event);
}
