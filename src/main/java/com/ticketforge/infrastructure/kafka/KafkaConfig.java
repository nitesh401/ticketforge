package com.ticketforge.infrastructure.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.apache.kafka.clients.admin.NewTopic;

@Configuration
@ConditionalOnProperty(name = "ticketforge.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Bean
    NewTopic bookingEvents() {
        return TopicBuilder.name(Topics.BOOKING_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentEvents() {
        return TopicBuilder.name(Topics.PAYMENT_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic seatEvents() {
        return TopicBuilder.name(Topics.SEAT_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic notificationEvents() {
        return TopicBuilder.name(Topics.NOTIFICATION_EVENTS).partitions(3).replicas(1).build();
    }

    /** Retries with exponential backoff, then parks the record on "<topic>.DLT" for inspection. */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(5_000);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
    }
}
