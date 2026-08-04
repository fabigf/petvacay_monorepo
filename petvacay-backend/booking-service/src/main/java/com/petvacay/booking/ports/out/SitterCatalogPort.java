package com.petvacay.booking.ports.out;

import com.petvacay.shared.domain.Money;

import java.util.Optional;
import java.util.UUID;

/**
 * SPI hacia el contexto de identidad: el modulo de reservas solo necesita los
 * datos comerciales del cuidador, no su modelo completo. En Fase 4 este puerto
 * pasa a resolverse por HTTP contra {@code identity-service} sin tocar el dominio.
 */
public interface SitterCatalogPort {

    Optional<SitterSummary> findSitter(UUID sitterId);

    /** Datos minimos del cuidador necesarios para validar y tarifar una reserva. */
    record SitterSummary(
            UUID sitterId,
            String firstName,
            boolean verified,
            Money pricePerNight,
            int maxSimultaneousPets) {
    }
}
