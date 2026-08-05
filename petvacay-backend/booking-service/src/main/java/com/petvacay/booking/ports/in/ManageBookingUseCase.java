package com.petvacay.booking.ports.in;

import com.petvacay.booking.domain.model.Booking;
import com.petvacay.booking.domain.model.DisputeResolution;

import java.math.BigDecimal;
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

    /**
     * Alerta medica del cuidador (negocio.md 5). Solo puede darla el cuidador de
     * la reserva: es quien esta con el animal.
     */
    Booking raisePetEmergency(UUID bookingId, UUID actingUserId, String description);

    /**
     * Saca una reserva de EN_DISPUTA con la resolucion manual del equipo de
     * administracion (negocio.md 4.4). No recibe {@code actingUserId} para
     * comprobar pertenencia — quien resuelve no es parte de la reserva, sino un
     * administrador — pero si para dejar registrado quien dicto la resolucion.
     *
     * @param refundToClient importe devuelto al cliente; obligatorio solo cuando
     *                       la resolucion es {@code SPLIT}, ignorado en el resto.
     */
    Booking resolveDispute(ResolveDisputeCommand command);

    record ResolveDisputeCommand(
            UUID bookingId,
            UUID resolvedBy,
            DisputeResolution resolution,
            BigDecimal refundToClient,
            String notes) {
    }

    /** Barrido de solicitudes caducadas; lo invoca el scheduler, sin usuario. */
    int expireStaleRequests();

    /**
     * Pasa a EN_PROGRESO las reservas confirmadas cuya fecha de inicio ya llego.
     * Lo invoca el scheduler: {@code negocio.md} describe el ciclo de vida como
     * automatico, no como algo que las partes tengan que pulsar.
     */
    int startDueBookings();

    /**
     * Finaliza las reservas en curso cuya ventana de disputa de 24h ya expiro,
     * lo que emite {@code ReservaFinalizadaEvent} y con el autoriza la
     * liquidacion al cuidador (negocio.md seccion 3.2). Sin este barrido el
     * cuidador solo cobraria si alguien llamara al endpoint a mano.
     */
    int releaseDuePayouts();
}
