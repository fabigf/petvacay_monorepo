package com.petvacay.booking.ports.out;

import com.petvacay.booking.domain.model.ServiceType;
import com.petvacay.shared.domain.Money;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SPI hacia el contexto de identidad: el modulo de reservas solo necesita los
 * datos comerciales del cuidador, no su modelo completo. En Fase 4 este puerto
 * pasa a resolverse por HTTP contra {@code identity-service} sin tocar el dominio.
 */
public interface SitterCatalogPort {

    Optional<SitterSummary> findSitter(UUID sitterId);

    /**
     * Datos minimos del cuidador necesarios para validar y tarifar una reserva.
     *
     * <p>{@code servicePrices} no siempre tiene las tres claves de
     * {@link ServiceType}: un cuidador puede ofrecer solo alojamiento, o solo
     * paseos. Pedir un servicio que no esta en el mapa es un caso de negocio
     * (el cuidador no lo ofrece), no un fallo tecnico — lo resuelve
     * {@code BookingApplicationService}, no este puerto.
     */
    record SitterSummary(
            UUID sitterId,
            String firstName,
            boolean verified,
            Map<ServiceType, Money> servicePrices,
            int maxSimultaneousPets) {
    }
}
