package com.petvacay.booking.ports.in;

import com.petvacay.booking.domain.model.Review;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Casos de uso de resenas: dejar una y consultarlas. */
public interface ReviewUseCase {

    /**
     * El autor se deduce de quien esta autenticado (igual que el resto de
     * transiciones de reserva): si es el cliente de la reserva, valora al
     * cuidador; si es el cuidador, valora al cliente.
     */
    Review leaveReview(UUID bookingId, UUID authorId, int rating, String comment);

    /** Las (hasta dos) resenas de una reserva concreta; solo visibles para sus participantes. */
    List<Review> listForBooking(UUID bookingId, UUID actingUserId);

    /** Resenas publicas recibidas por un cuidador, mas recientes primero. */
    List<Review> listForSitter(UUID sitterId);

    /**
     * Crea la resena negativa automatica que negocio.md 4.3 exige cuando un
     * cuidador cancela una reserva ya confirmada. Devuelve vacio si la
     * cancelacion no da lugar a penalizacion (la cancelo el cliente, o la reserva
     * ni siquiera estaba pagada) o si ya se genero antes.
     */
    Optional<Review> penalizeSitterCancellation(UUID bookingId);
}
