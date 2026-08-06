package com.petvacay.booking.ports.out;

import java.util.Set;
import java.util.UUID;

/**
 * Consulta de que mascotas pertenecen a un usuario.
 *
 * <p>El agregado {@code Booking} guarda identificadores de mascotas que viven en
 * otro contexto (identidad), asi que no puede validar por si mismo que sean del
 * cliente: sin esta comprobacion bastaba con conocer el UUID de la mascota de
 * otra persona para reservarle un cuidador a su nombre.
 */
public interface ClientPetsPort {

    /** Identificadores de las mascotas del usuario; vacio si no tiene ninguna. */
    Set<UUID> petsOwnedBy(UUID ownerId);

    /**
     * Fichas tecnicas de las mascotas de una reserva, para que el cuidador sepa
     * a que animal se compromete (negocio.md 2.1).
     */
    java.util.List<PetDetails> detailsOf(java.util.Collection<UUID> petIds);

    /**
     * Copia local de la ficha que expone identity-service. Se duplica aqui en
     * vez de compartir el DTO por el mismo motivo que {@code SitterSummary}:
     * compartir el modelo compilado de un endpoint interno acopla el despliegue
     * de ambos servicios.
     */
    record PetDetails(
            UUID petId,
            String name,
            String species,
            String breed,
            int ageYears,
            java.math.BigDecimal weightKg,
            String medicalNeeds,
            String dietaryRestrictions,
            String sociability,
            boolean specialMedicalNeeds) {
    }
}
