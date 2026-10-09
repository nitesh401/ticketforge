package com.ticketforge.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketforge.infrastructure.events.DomainEvent;
import com.ticketforge.infrastructure.events.EventSink;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "ticketforge.kafka.enabled", havingValue = "true")
public class KafkaEventSink implements EventSink {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public KafkaEventSink(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @Override
    public void send(DomainEvent event) {
        try {
            // Keyed by aggregate id so all events of one booking/hold stay ordered within a partition.
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(event.topic(), event.key(), mapper.writeValueAsString(event));
            record.headers().add(new RecordHeader("event-id", event.eventId().getBytes(StandardCharsets.UTF_8)));
            kafka.send(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + event.eventId(), e);
        }
    }
}
