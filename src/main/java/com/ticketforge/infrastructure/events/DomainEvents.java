package com.ticketforge.infrastructure.events;

import java.time.Clock;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Services call publish() inside their transaction. The event is only forwarded to the sink after
 * the transaction commits, so rolled-back work never leaks events.
 *
 * This is still a dual write (DB commit, then broker send). A crash between the two loses the event;
 * the production fix is a transactional outbox, discussed in docs/failure-scenarios.md.
 */
@Component
public class DomainEvents {
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public DomainEvents(ApplicationEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    public void publish(String topic, String type, String key, Map<String, Object> payload) {
        publisher.publishEvent(DomainEvent.of(topic, type, key, clock.instant(), payload));
    }
}
