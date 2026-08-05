package com.petvacay.search.domain.model;

import com.petvacay.identity.domain.model.HomeType;
import com.petvacay.identity.domain.model.ServiceType;
import com.petvacay.identity.domain.model.Species;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vista plana desnormalizada de un cuidador: es el lado de lectura del CQRS.
 *
 * <p>En Fase 1 se materializa en PostgreSQL para no adelantar infraestructura;
 * en Fase 2 el mismo contrato se persiste en MongoDB cambiando solo el adaptador.
 * {@code occupancyByDate} guarda el numero de mascotas ya comprometidas por noche,
 * que es lo que permite descartar cuidadores sin capacidad sin consultar al modelo
 * de escritura.
 */
public record SitterView(
        UUID sitterId,
        String firstName,
        BigDecimal rating,
        int reviewCount,
        BigDecimal pricePerNight,
        String currency,
        HomeType homeType,
        double latitude,
        double longitude,
        int maxSimultaneousPets,
        Set<Species> acceptedSpecies,
        Set<ServiceType> offeredServices,
        BigDecimal maxPetWeightKg,
        String photoUrl,
        Map<LocalDate, Integer> occupancyByDate,
        /** Reservas ya confirmadas que este cuidador ha cancelado (negocio.md 4.3). */
        int cancellationCount) {

    /**
     * Cuanto "aleja" del cliente cada cancelacion de una reserva confirmada.
     *
     * <p>La penalizacion de posicionamiento que pide negocio.md 4.3 se expresa en
     * la misma unidad con la que ya se ordena el buscador — kilometros — en vez de
     * inventar una puntuacion nueva: es medible ("cancelar cuesta lo mismo que
     * estar 10 km mas lejos"), no rompe el criterio principal de cercania y no
     * puede expulsar a nadie del resultado, solo bajarlo.
     */
    public static final double CANCELLATION_PENALTY_KM = 10;

    /** Techo de la penalizacion: a partir de aqui, cancelar mas no hunde mas. */
    public static final double MAX_PENALTY_KM = 50;

    /**
     * Distancia con la que este cuidador compite en el listado. La distancia real
     * sigue siendo {@code distanceKm} y es la que se muestra al usuario: esto solo
     * afecta al orden.
     */
    public double rankingPenaltyKm() {
        return Math.min(cancellationCount * CANCELLATION_PENALTY_KM, MAX_PENALTY_KM);
    }

    /** Capacidad libre para todas las noches del rango [start, end). */
    public boolean hasCapacity(LocalDate start, LocalDate end, int petCount) {
        for (LocalDate night = start; night.isBefore(end); night = night.plusDays(1)) {
            int occupied = occupancyByDate.getOrDefault(night, 0);
            if (occupied + petCount > maxSimultaneousPets) {
                return false;
            }
        }
        return true;
    }

    public boolean accepts(Species species) {
        return acceptedSpecies.contains(species);
    }
}
