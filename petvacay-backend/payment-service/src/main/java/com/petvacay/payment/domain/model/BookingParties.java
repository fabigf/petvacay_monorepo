package com.petvacay.payment.domain.model;

import java.util.UUID;

/**
 * Quiénes son el cliente y el cuidador de una reserva.
 *
 * <p>payment-service necesita este dato para responder a una pregunta que antes
 * no podía contestar: *¿tiene este usuario derecho a ver el libro mayor de esta
 * reserva?*. El dato vive en booking-service, así que hay dos formas de
 * obtenerlo — preguntar por HTTP en cada consulta, o quedarse con el que ya
 * viaja en los eventos de reserva.
 *
 * <p>Se eligió lo segundo, por el mismo razonamiento que llevó a
 * {@code CuidadorIndexadoEvent} en la Fase 4: {@code PagoConfirmadoEvent} ya
 * transporta {@code clientId} y {@code sitterId}, y guardarlos aquí mantiene a
 * payment-service capaz de responder aunque booking-service esté caído. No es
 * duplicar la reserva: son los dos identificadores mínimos que la autorización
 * necesita.
 */
public record BookingParties(UUID bookingId, UUID clientId, UUID sitterId) {

    /** El libro mayor de una reserva solo lo pueden ver sus dos partes. */
    public boolean includes(UUID userId) {
        return clientId.equals(userId) || sitterId.equals(userId);
    }
}
