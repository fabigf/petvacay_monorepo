package com.petvacay.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Clock;

@Configuration
@EnableJpaRepositories(basePackages = "com.petvacay.booking.adapters.out.persistence")
public class ApplicationConfig {

    /**
     * Reloj inyectable: el dominio nunca llama a {@code Instant.now()}, lo que hace
     * testeables las reglas temporales (expiracion de 24h, ventana de 48h, escrow).
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
