package com.ticketforge.infrastructure.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DomainEvent(String eventId, String topic, String type, String key, Instant occurredAt,
                          Map<String, Object> payload) {

    public static DomainEvent of(String topic, String type, String key, Instant now, Map<String, Object> payload) {
        return new DomainEvent(UUID.randomUUID().toString(), topic, type, key, now, payload);
    }
}
