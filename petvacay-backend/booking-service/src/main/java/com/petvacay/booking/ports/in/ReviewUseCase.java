package com.petvacay.booking.ports.in;

import com.petvacay.booking.domain.model.Review;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Casos de uso de resenas: dejar una y consultarlas. */
public interface ReviewUseCase {

    /**
     * Valoracion del cliente sobre el cuidador que le prestó el servicio.
     *
     * <p>El autor se deduce de quien esta autenticado, igual que el resto de
     * transiciones de reserva. Solo puede valorar el cliente: el cuidador ve las
     * resenas que recibe pero no escribe ninguna, ni sobre si mismo ni sobre sus
     * clientes.
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

    /**
     * Republica la reputacion de todos los cuidadores valorados con sus valores
     * absolutos, para que el buscador la reconstruya. Devuelve a cuantos afecta.
     *
     * <p>Existe porque la proyeccion puede desincronizarse del modelo de
     * escritura — pasó de hecho: el rating agregado se guardaba en Mongo como
     * cadena y ningun incremento llegaba a aplicarse, asi que las resenas
     * existian en booking-service pero el buscador seguia mostrando cero.
     */
    int reprojectReputations();
}
