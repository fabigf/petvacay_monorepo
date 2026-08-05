package com.petvacay.booking.application;

import com.petvacay.booking.domain.event.BookingEvents;
import com.petvacay.booking.domain.model.CancellationActor;
import com.petvacay.booking.domain.model.CancellationOutcome;
import com.petvacay.booking.domain.model.Notification;
import com.petvacay.booking.domain.model.NotificationType;
import com.petvacay.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Traduce eventos de dominio a avisos de la campana.
 *
 * <p>Se alimenta de los mismos eventos que ya publica el servicio en lugar de
 * repartir llamadas "avisa de esto" por cada caso de uso: si una transicion
 * ocurre, su evento existe, y con el la notificacion — no hay forma de anadir
 * una transicion nueva y olvidarse de avisar en unos flujos si y en otros no.
 *
 * <p>El texto se redacta aqui, en castellano y en segunda persona, porque es
 * texto de producto: el dominio no deberia saber como se le habla al usuario.
 */
final class NotificationFactory {

    private NotificationFactory() {
    }

    static List<Notification> from(DomainEvent event, Instant now) {
        if (event instanceof BookingEvents.ReservaSolicitadaEvent e) {
            return List.of(to(e.sitterId(), NotificationType.BOOKING_REQUESTED,
                    "Tienes una reserva pendiente",
                    "Un cliente ha solicitado tus servicios del " + e.dates().start()
                            + " al " + e.dates().end() + ". Tienes 24 horas para responder.",
                    e.bookingId(), now));
        }

        if (event instanceof BookingEvents.ReservaAceptadaEvent e) {
            return List.of(to(e.clientId(), NotificationType.BOOKING_ACCEPTED,
                    "El cuidador ha aceptado tu solicitud",
                    "Completa el pago de " + e.amountToPay() + " para confirmar la reserva.",
                    e.bookingId(), now));
        }

        if (event instanceof BookingEvents.PagoConfirmadoEvent e) {
            return List.of(
                    to(e.clientId(), NotificationType.BOOKING_CONFIRMED, "Reserva confirmada",
                            "Tu pago se ha confirmado y la reserva queda cerrada en firme.",
                            e.bookingId(), now),
                    to(e.sitterId(), NotificationType.BOOKING_CONFIRMED, "Reserva confirmada",
                            "El cliente ha pagado: estas fechas ya están bloqueadas en tu calendario.",
                            e.bookingId(), now));
        }

        if (event instanceof BookingEvents.ReservaCanceladaEvent e) {
            return cancellationNotifications(e, now);
        }

        if (event instanceof BookingEvents.ReservaFinalizadaEvent e) {
            return List.of(to(e.sitterId(), NotificationType.BOOKING_FINISHED, "Servicio finalizado",
                    "El plazo de incidencias ha pasado: se han liberado " + e.sitterPayout() + " a tu favor.",
                    e.bookingId(), now));
        }

        if (event instanceof BookingEvents.DisputaAbiertaEvent e) {
            return List.of(to(Notification.ADMIN_AUDIENCE, NotificationType.DISPUTE_OPENED,
                    "Incidencia por resolver",
                    "Se ha abierto una disputa: " + e.reason(), e.bookingId(), now));
        }

        if (event instanceof BookingEvents.DisputaResueltaEvent e) {
            String body = "El equipo de administración ha resuelto la incidencia. Reembolso al cliente: "
                    + e.outcome().refundToClient() + ".";
            return List.of(
                    to(e.clientId(), NotificationType.DISPUTE_RESOLVED, "Incidencia resuelta", body,
                            e.bookingId(), now),
                    to(e.sitterId(), NotificationType.DISPUTE_RESOLVED, "Incidencia resuelta", body,
                            e.bookingId(), now));
        }

        if (event instanceof BookingEvents.MascotaEnPeligroEvent e) {
            // Al dueño y al equipo de soporte a la vez: negocio.md 5 pide avisar
            // por todos los canales y coordinar asistencia veterinaria.
            String body = "El cuidador ha dado una alerta médica: " + e.description();
            return List.of(
                    to(e.clientId(), NotificationType.PET_EMERGENCY, "Alerta médica sobre tu mascota",
                            body, e.bookingId(), now),
                    to(Notification.ADMIN_AUDIENCE, NotificationType.PET_EMERGENCY,
                            "Alerta médica en una reserva", body, e.bookingId(), now));
        }

        // Resenas y demas eventos no generan aviso: no hay nada que el usuario
        // tenga que atender.
        return List.of();
    }

    /**
     * Quien recibe el aviso de una cancelacion es siempre "el otro": a quien
     * cancelo no hay que contarle lo que acaba de hacer. Si canceló el sistema
     * (la solicitud caducó) se avisa a los dos.
     */
    private static List<Notification> cancellationNotifications(BookingEvents.ReservaCanceladaEvent e,
                                                                Instant now) {
        // Rechazar una solicitud todavia sin pagar no es "cancelar una reserva":
        // no hay dinero de por medio y decirle al cliente que se le devolvera el
        // importe seria falso. El desglose economico distingue ambos casos.
        boolean neverPaid = e.outcome().policyApplied()
                == CancellationOutcome.CancellationPolicyKind.NOT_YET_FUNDED;

        Notification forClient = to(e.clientId(), NotificationType.BOOKING_REJECTED,
                neverPaid ? "Solicitud rechazada" : "Reserva cancelada",
                switch (e.cancelledBy()) {
                    case SITTER -> neverPaid
                            ? "El cuidador ha rechazado tu solicitud. Puedes buscar otro cuidador."
                            : "El cuidador ha cancelado la reserva. Se te devolverá el importe íntegro.";
                    default -> "La solicitud ha caducado sin respuesta del cuidador.";
                },
                e.bookingId(), now);

        Notification forSitter = to(e.sitterId(), NotificationType.BOOKING_CANCELLED, "Reserva cancelada",
                e.cancelledBy() == CancellationActor.CLIENT
                        ? "El cliente ha cancelado la reserva."
                        : "La solicitud ha caducado y se ha cancelado automáticamente.",
                e.bookingId(), now);

        return switch (e.cancelledBy()) {
            case CLIENT -> List.of(forSitter);
            case SITTER -> List.of(forClient);
            case SYSTEM -> List.of(forClient, forSitter);
        };
    }

    private static Notification to(UUID recipientId, NotificationType type, String title, String body,
                                   UUID bookingId, Instant now) {
        return Notification.create(recipientId, type, title, body, bookingId, now);
    }
}
