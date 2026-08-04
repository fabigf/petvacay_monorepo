package com.petvacay.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationConfig {

    /**
     * Reloj inyectable, igual que en el resto de servicios: nada llama a
     * {@code Instant.now()} directamente, de modo que el tiempo es un dato
     * controlable en los tests.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
