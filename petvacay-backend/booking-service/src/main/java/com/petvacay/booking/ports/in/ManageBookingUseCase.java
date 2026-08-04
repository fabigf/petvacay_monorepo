package com.petvacay.booking.ports.in;

import com.petvacay.booking.domain.model.Booking;

import java.util.UUID;

/**
 * Casos de uso que hacen avanzar la maquina de estados de una reserva.
 *
 * <p>Desde la Fase 5 todos reciben el {@code actingUserId} del usuario
 * autenticado. No es un dato decorativo: el servicio de aplicacion lo usa para
 * comprobar que quien invoca la operacion es realmente parte de esa reserva (su
 * cliente o su cuidador) y para **derivar** el rol con el que actua. Antes, el
 * rol viajaba como parametro de la peticion, lo que permitia a un cliente
 * cancelar declarandose cuidador y cobrar asi un reembolso integro en lugar de
 * perder la primera noche.
 */
public interface ManageBookingUseCase {

    Booking acceptBySitter(UUID bookingId, UUID actingUserId);

    Booking rejectBySitter(UUID bookingId, UUID actingUserId);

    Booking confirmPayment(UUID bookingId, UUID actingUserId);

    Booking failPayment(UUID bookingId, UUID actingUserId);

    Booking start(UUID bookingId, UUID actingUserId);

    Booking complete(UUID bookingId, UUID actingUserId);

    /**
     * Cancela la reserva. El {@code CancellationActor} — y con el la politica de
     * reembolso que se aplica — se deduce de si {@code actingUserId} es el cliente
     * o el cuidador de la reserva; nunca de lo que diga la peticion.
     */
    Booking cancel(UUID bookingId, UUID actingUserId);

    Booking openDispute(UUID bookingId, UUID actingUserId, String reason);

    /** Barrido de solicitudes caducadas; lo invoca el scheduler, sin usuario. */
    int expireStaleRequests();
}
