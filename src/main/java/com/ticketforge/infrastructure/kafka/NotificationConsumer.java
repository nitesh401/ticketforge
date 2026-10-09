package com.ticketforge.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka delivers at-least-once, so the same event can arrive twice. The consumer records each
 * event id in processed_events inside the same transaction as its side effect; a duplicate
 * delivery hits ON CONFLICT DO NOTHING and is skipped.
 */
@Component
@ConditionalOnProperty(name = "ticketforge.kafka.enabled", havingValue = "true")
public class NotificationConsumer {
    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);
    private static final String CONSUMER = "notification-service";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public NotificationConsumer(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @KafkaListener(topics = {Topics.BOOKING_EVENTS, Topics.PAYMENT_EVENTS, Topics.SEAT_EVENTS}, groupId = CONSUMER)
    @Transactional
    public void onMessage(String json) throws JsonProcessingException {
        JsonNode event = mapper.readTree(json);
        String eventId = event.get("eventId").asText();
        int inserted = jdbc.update(
                "INSERT INTO processed_events (event_id, consumer) VALUES (?, ?) ON CONFLICT DO NOTHING",
                eventId, CONSUMER);
        if (inserted == 0) {
            log.info("Duplicate event {} ignored", eventId);
            return;
        }
        log.info("Notification for {} key={}", event.get("type").asText(), event.get("key").asText());
    }
}
