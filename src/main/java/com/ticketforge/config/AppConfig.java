package com.ticketforge.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(TicketForgeProperties.class)
public class AppConfig {

    /** Injected everywhere instead of Instant.now() so tests can move time deterministically. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
