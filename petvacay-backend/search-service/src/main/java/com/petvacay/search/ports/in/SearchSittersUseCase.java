package com.petvacay.search.ports.in;

import com.petvacay.identity.domain.model.HomeType;
import com.petvacay.identity.domain.model.ServiceType;
import com.petvacay.identity.domain.model.Species;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lado de consulta del CQRS: alimenta el modulo `search` de React. */
public interface SearchSittersUseCase {

    List<SitterSearchResult> search(SearchQuery query);

    record SearchQuery(
            double lat,
            double lng,
            Double radiusKm,
            LocalDate startDate,
            LocalDate endDate,
            Species petType,
            Integer petCount) {

        public static final double DEFAULT_RADIUS_KM = 25;

        public double effectiveRadiusKm() {
            return radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
        }

        public int effectivePetCount() {
            return petCount == null ? 1 : petCount;
        }
    }

    /** Contrato de respuesta definido en especificaciones.md seccion 1.1. */
    record SitterSearchResult(
            UUID sitterId,
            String firstName,
            BigDecimal rating,
            int reviewCount,
            double distanceKm,
            BigDecimal pricePerNight,
            String currency,
            HomeType homeType,
            String photoUrl,
            Set<Species> acceptedSpecies,
            Set<ServiceType> offeredServices,
            // Plazas del cuidador. El modelo de lectura ya lo guardaba para filtrar
            // por capacidad, pero no salia de ahi; es informacion que el cliente
            // quiere ver antes de reservar ("hasta 2 mascotas a la vez").
            int maxSimultaneousPets) {
    }
}
