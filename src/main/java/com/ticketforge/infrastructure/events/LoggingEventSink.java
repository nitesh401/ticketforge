package com.ticketforge.infrastructure.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default sink when Kafka is disabled: events are visible in logs only. */
@Component
@ConditionalOnProperty(name = "ticketforge.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEventSink implements EventSink {
    private static final Logger log = LoggerFactory.getLogger(LoggingEventSink.class);

    @Override
    public void send(DomainEvent event) {
        log.info("event topic={} type={} key={} id={}", event.topic(), event.type(), event.key(), event.eventId());
    }
}
