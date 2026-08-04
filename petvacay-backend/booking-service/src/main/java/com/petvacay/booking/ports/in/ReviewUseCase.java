package com.petvacay.booking.ports.in;

import com.petvacay.booking.domain.model.Review;

import java.util.List;
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
}
