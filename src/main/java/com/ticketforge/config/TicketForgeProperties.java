package com.ticketforge.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ticketforge")
public record TicketForgeProperties(Hold hold, Retry retry, Security security, WaitingRoom waitingRoom) {

    public record Hold(Duration duration, String defaultStrategy) {
    }

    public record Retry(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
    }

    public record Security(String jwtSecret, Duration jwtTtl, boolean devTokenEndpointEnabled,
                           String paymentWebhookSecret) {
    }

    public record WaitingRoom(boolean enabled, int admitPerSecond, Duration admissionTtl, int maxQueueSize) {
    }
}
