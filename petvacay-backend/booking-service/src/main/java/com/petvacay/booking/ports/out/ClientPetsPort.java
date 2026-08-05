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
}
