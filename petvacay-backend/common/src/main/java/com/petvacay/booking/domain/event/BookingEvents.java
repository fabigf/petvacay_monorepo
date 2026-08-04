package com.petvacay.booking.domain.event;

import com.petvacay.booking.domain.model.CancellationActor;
import com.petvacay.booking.domain.model.CancellationOutcome;
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
}
