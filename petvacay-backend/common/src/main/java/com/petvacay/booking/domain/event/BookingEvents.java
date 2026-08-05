package com.petvacay.booking.domain.event;

import com.petvacay.booking.domain.model.CancellationActor;
import com.petvacay.booking.domain.model.CancellationOutcome;
import com.petvacay.booking.domain.model.DisputeResolution;
import com.petvacay.shared.domain.DateRange;
import com.petvacay.shared.domain.DomainEvent;
import com.petvacay.shared.domain.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Eventos de dominio del contexto de reservas (negocio.md seccion 5).
 * Se publican con el bus interno de Spring en Fase 1-2 y con Kafka en Fase 3;
 * el payload no cambia, solo el transporte.
 *
 * <p>Cada evento declara su {@code TOPIC} como constante publica: es la unica
 * fuente de verdad que usan tanto {@code eventName()} (el bus interno de Spring no
 * necesita mas) como las anotaciones {@code @KafkaListener} de los consumidores, que
 * exigen una constante de tiempo de compilacion y no pueden invocar un metodo de
 * instancia.
 */
public final class BookingEvents {

    private BookingEvents() {
    }

    /** Dispara notificaciones push/email de alta prioridad al cuidador. */
    public record ReservaSolicitadaEvent(
            UUID eventId,
            Instant occurredAt,
            UUID bookingId,
            UUID clientId,
            UUID sitterId,
            DateRange dates,
            Money amountToPay) implements DomainEvent {

        public static final String TOPIC = "petvacay.booking.reserva-solicitada";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return bookingId;
        }
    }

    /**
     * El cuidador acepto la solicitud y la pelota pasa al cliente, que tiene que
     * pagar. Nace con el centro de notificaciones: la transicion existia desde la
     * Fase 1 pero no publicaba nada, asi que el cliente solo se enteraba de que le
     * habian aceptado si volvia a mirar su lista de reservas por su cuenta.
     */
    public record ReservaAceptadaEvent(
            UUID eventId,
            Instant occurredAt,
            UUID bookingId,
            UUID clientId,
            UUID sitterId,
            Money amountToPay) implements DomainEvent {

        public static final String TOPIC = "petvacay.booking.reserva-aceptada";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return bookingId;
        }
    }

    /**
     * Exito financiero: confirma al cliente, bloquea el calendario en el modelo de
     * lectura y programa la liberacion de fondos.
     */
    public record PagoConfirmadoEvent(
            UUID eventId,
            Instant occurredAt,
            UUID bookingId,
            UUID clientId,
            UUID sitterId,
            DateRange dates,
            int petCount,
            Money totalCharged,
            Instant scheduledPayoutAt) implements DomainEvent {

        public static final String TOPIC = "petvacay.booking.pago-confirmado";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return bookingId;
        }
    }

    /** La reserva quedo cancelada; el payload lleva el desglose de reembolsos. */
    public record ReservaCanceladaEvent(
            UUID eventId,
            Instant occurredAt,
            UUID bookingId,
            UUID clientId,
            UUID sitterId,
            DateRange dates,
            int petCount,
            CancellationActor cancelledBy,
            CancellationOutcome outcome) implements DomainEvent {

        public static final String TOPIC = "petvacay.booking.reserva-cancelada";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return bookingId;
        }
    }

    /** Servicio finalizado y ventana de disputa expirada: procede la liquidacion. */
    public record ReservaFinalizadaEvent(
            UUID eventId,
            Instant occurredAt,
            UUID bookingId,
            UUID sitterId,
            DateRange dates,
            int petCount,
            Money sitterPayout,
            Money platformRevenue) implements DomainEvent {

        public static final String TOPIC = "petvacay.booking.reserva-finalizada";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return bookingId;
        }
    }

    /**
     * Alerta medica levantada por el cuidador durante el servicio (negocio.md 5,
     * "MascotaEnPeligroEvent"). Es el evento mas urgente de la plataforma: avisa
     * al dueno de inmediato y pone sobre aviso al equipo de soporte.
     *
     * <p>No cambia el estado de la reserva a proposito. Una emergencia medica no
     * es una disputa — el servicio sigue en curso y el cuidador necesita seguir
     * atendiendo al animal — y congelar el escrow aqui castigaria justo la
     * conducta que se quiere fomentar: avisar cuanto antes.
     */
    public record MascotaEnPeligroEvent(
            UUID eventId,
            Instant occurredAt,
            UUID bookingId,
            UUID sitterId,
            UUID clientId,
            java.util.List<UUID> petIds,
            String description) implements DomainEvent {

        public static final String TOPIC = "petvacay.booking.mascota-en-peligro";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return bookingId;
        }
    }

    /** Congela la liberacion automatica de fondos hasta resolucion manual. */
    public record DisputaAbiertaEvent(
            UUID eventId,
            Instant occurredAt,
            UUID bookingId,
            UUID openedBy,
            String reason) implements DomainEvent {

        public static final String TOPIC = "petvacay.booking.disputa-abierta";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return bookingId;
        }
    }

    /**
     * El equipo de administracion dicto una resolucion manual sobre una disputa
     * (negocio.md 4.4). Descongela el escrow: lleva el reparto exacto que
     * payment-service debe asentar en el libro mayor, porque en una disputa ese
     * reparto no lo decide ninguna politica automatica.
     */
    public record DisputaResueltaEvent(
            UUID eventId,
            Instant occurredAt,
            UUID bookingId,
            UUID clientId,
            UUID sitterId,
            // Los lleva para que search-service pueda liberar el calendario sin
            // volver a preguntar: una reserva en disputa lo mantiene bloqueado.
            DateRange dates,
            int petCount,
            UUID resolvedBy,
            DisputeResolution resolution,
            String resolutionNotes,
            CancellationOutcome outcome) implements DomainEvent {

        public static final String TOPIC = "petvacay.booking.disputa-resuelta";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return bookingId;
        }
    }

    /**
     * Una parte valoro la reserva finalizada (negocio.md: "resenas cruzadas
     * inmutables"). Solo se publica cuando el autor es el CLIENTE: es la unica
     * direccion que afecta a la reputacion publica del cuidador en el buscador.
     * Una resena del cuidador hacia el cliente se guarda igual en booking-service,
     * pero no tiene ningun modelo de lectura que la consuma todavia, asi que no
     * dispara evento — nada estaria escuchando.
     */
    public record ResenaCreadaEvent(
            UUID eventId,
            Instant occurredAt,
            UUID bookingId,
            UUID reviewId,
            UUID sitterId,
            int rating) implements DomainEvent {

        public static final String TOPIC = "petvacay.booking.resena-creada";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return bookingId;
        }
    }
}
