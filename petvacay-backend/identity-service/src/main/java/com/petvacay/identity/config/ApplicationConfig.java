package com.petvacay.identity.config;

import com.petvacay.identity.ports.out.IdentityPorts.PasswordHasherPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration
@EnableJpaRepositories(basePackages = "com.petvacay.identity.adapters.out.persistence")
public class ApplicationConfig {

    /**
     * Reloj inyectable: el dominio nunca llama a {@code Instant.now()}, lo que hace
     * testeables las reglas temporales (mayoria de edad del cuidador, timestamps de
     * KYC, caducidad del token).
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * BCrypt (Fase 5), sustituyendo al SHA-256 provisional de las Fases 1-4.
     *
     * <p>SHA-256 es un hash rapido de proposito general: precisamente lo que NO se
     * quiere para contrasenas, porque un atacante con la base de datos podria
     * probar miles de millones de candidatas por segundo. BCrypt incorpora sal
     * automatica y un factor de coste configurable que hace el calculo
     * deliberadamente lento.
     *
     * <p>El puerto {@link PasswordHasherPort} no ha cambiado: el dominio sigue sin
     * saber que algoritmo hay detras, que era el objetivo de definirlo asi en la
     * Fase 1.
     */
    @Bean
    public PasswordHasherPort passwordHasher() {
        return new BCryptPasswordHasher(new BCryptPasswordEncoder());
    }

    static class BCryptPasswordHasher implements PasswordHasherPort {

        private final PasswordEncoder encoder;

        BCryptPasswordHasher(PasswordEncoder encoder) {
            this.encoder = encoder;
        }

        @Override
        public String hash(String rawPassword) {
            return encoder.encode(rawPassword);
        }

        @Override
        public boolean matches(String rawPassword, String hash) {
            return encoder.matches(rawPassword, hash);
        }
    }
}
