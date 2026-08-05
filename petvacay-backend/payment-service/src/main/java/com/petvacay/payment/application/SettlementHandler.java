package com.petvacay.payment.application;

import com.petvacay.booking.domain.event.BookingEvents;
import com.petvacay.booking.domain.model.CancellationOutcome;
import com.petvacay.payment.domain.model.BookingParties;
import com.petvacay.payment.domain.model.LedgerEntry;
import com.petvacay.payment.domain.model.LedgerEntry.LedgerEntryType;
import com.petvacay.payment.ports.out.BookingPartiesRepositoryPort;
import com.petvacay.payment.ports.out.LedgerRepositoryPort;
import com.petvacay.payment.ports.out.ProcessedEventPort;
import com.petvacay.shared.domain.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Traduce los eventos de reserva en apuntes del libro mayor del escrow.
 *
 * <p>Logica de aplicacion pura, igual que {@code SitterProjectionHandler}: no sabe
 * si el evento llego por el bus interno de Spring o por Kafka. Fase 6: este mismo
 * handler disparara las llamadas reales a Stripe Connect y guardara la referencia
 * externa devuelta junto a cada apunte.
 */
@Component
public class SettlementHandler {

    private static final Logger log = LoggerFactory.getLogger(SettlementHandler.class);

    private final LedgerRepositoryPort ledger;
    private final BookingPartiesRepositoryPort bookingParties;
    private final ProcessedEventPort processedEvents;

    public SettlementHandler(LedgerRepositoryPort ledger,
                             BookingPartiesRepositoryPort bookingParties,
                             ProcessedEventPort processedEvents) {
        this.ledger = ledger;
        this.bookingParties = bookingParties;
        this.processedEvents = processedEvents;
    }

    /**
     * Se registra quien es cliente y quien cuidador en cuanto existe la reserva.
     *
     * <p>Tiene que ser aqui y no en {@link #onPagoConfirmado}: {@code preparePayment}
     * exige conocer las partes ANTES de cobrar (para validar que quien paga es el
     * cliente y no un tercero), mientras que {@code PagoConfirmadoEvent} solo se
     * emite DESPUES del cobro. Aprender las partes unicamente en ese evento creaba
     * un bloqueo mutuo: no se podia pagar porque no se conocian las partes, y no se
     * conocian las partes porque no se habia pagado.
     *
     * <p>No pasa por el registro de idempotencia: la escritura va contra la clave
     * primaria {@code bookingId}, asi que reprocesar el evento reescribe la misma
     * fila con los mismos valores. Anotar el evento solo anadiria una fila inutil
     * a la tabla de deduplicacion.
     */
    @Transactional
    public void onReservaSolicitada(BookingEvents.ReservaSolicitadaEvent event) {
        bookingParties.save(new BookingParties(event.bookingId(), event.clientId(), event.sitterId()));
        log.debug("Partes registradas para la reserva {}", event.bookingId());
    }

    /** El cliente ha pagado: el importe integro queda retenido en custodia. */
    @Transactional
    public void onPagoConfirmado(BookingEvents.PagoConfirmadoEvent event) {
        if (isDuplicate(event.eventId(), event.eventName())) {
            return;
        }

        // Reescritura defensiva: normalmente ya se registraron al solicitarse la
        // reserva, pero si aquel evento se perdio esta es la ultima oportunidad de
        // saber quien tiene derecho a consultar este libro mayor sin tener que
        // preguntar a booking-service en cada lectura.
        bookingParties.save(new BookingParties(event.bookingId(), event.clientId(), event.sitterId()));

        ledger.append(LedgerEntry.of(event.bookingId(), LedgerEntryType.ESCROW_HOLD,
                event.clientId(), event.totalCharged(), event.occurredAt()));
        log.info("Escrow retenido para la reserva {}: {}", event.bookingId(), event.totalCharged());
    }

    /** Expirada la ventana de disputa se reparte el escrow. */
    @Transactional
    public void onReservaFinalizada(BookingEvents.ReservaFinalizadaEvent event) {
        if (isDuplicate(event.eventId(), event.eventName())) {
            return;
        }

        ledger.append(LedgerEntry.of(event.bookingId(), LedgerEntryType.SITTER_PAYOUT,
                event.sitterId(), event.sitterPayout(), event.occurredAt()));
        ledger.append(LedgerEntry.of(event.bookingId(), LedgerEntryType.PLATFORM_REVENUE,
                null, event.platformRevenue(), event.occurredAt()));
        log.info("Fondos liberados para la reserva {}: cuidador {} / plataforma {}",
                event.bookingId(), event.sitterPayout(), event.platformRevenue());
    }

    /** Cancelacion: se aplica el desglose que resolvio la politica de dominio. */
    @Transactional
    public void onReservaCancelada(BookingEvents.ReservaCanceladaEvent event) {
        if (isDuplicate(event.eventId(), event.eventName())) {
            return;
        }

        CancellationOutcome outcome = event.outcome();
        if (outcome.policyApplied() == CancellationOutcome.CancellationPolicyKind.NOT_YET_FUNDED) {
            return;
        }

        appendIfPositive(event.bookingId(), event.occurredAt(),
                LedgerEntryType.CLIENT_REFUND, event.clientId(), outcome.refundToClient());
        appendIfPositive(event.bookingId(), event.occurredAt(),
                LedgerEntryType.SITTER_COMPENSATION, event.sitterId(), outcome.payoutToSitter());
        appendIfPositive(event.bookingId(), event.occurredAt(),
                LedgerEntryType.PLATFORM_REVENUE, null, outcome.platformRevenue());
    }

    /**
     * Disputa resuelta por un administrador: descongela el escrow con el reparto
     * que dictamino, sea cual sea. Es el unico camino por el que el dinero de una
     * reserva en disputa vuelve a moverse — hasta ahora se quedaba retenido
     * indefinidamente porque nada sacaba la reserva de EN_DISPUTA.
     */
    @Transactional
    public void onDisputaResuelta(BookingEvents.DisputaResueltaEvent event) {
        if (isDuplicate(event.eventId(), event.eventName())) {
            return;
        }

        CancellationOutcome outcome = event.outcome();
        appendIfPositive(event.bookingId(), event.occurredAt(),
                LedgerEntryType.CLIENT_REFUND, event.clientId(), outcome.refundToClient());
        appendIfPositive(event.bookingId(), event.occurredAt(),
                LedgerEntryType.SITTER_PAYOUT, event.sitterId(), outcome.payoutToSitter());
        appendIfPositive(event.bookingId(), event.occurredAt(),
                LedgerEntryType.PLATFORM_REVENUE, null, outcome.platformRevenue());
        log.info("Disputa de la reserva {} resuelta ({}): cliente {} / cuidador {} / plataforma {}",
                event.bookingId(), event.resolution(), outcome.refundToClient(),
                outcome.payoutToSitter(), outcome.platformRevenue());
    }

    /**
     * Descarta las reentregas de Kafka.
     *
     * <p>Kafka garantiza entrega "al menos una vez": un rebalanceo o un reinicio
     * antes de comprometer el offset bastan para recibir el mismo evento dos
     * veces. Sin esta comprobacion se duplicarian apuntes del libro mayor y las
     * cuentas dejarian de cuadrar. Va dentro de la misma transaccion que la
     * escritura de los apuntes: si algo falla despues, tambien se deshace la
     * marca y el evento podra reintentarse.
     */
    private boolean isDuplicate(java.util.UUID eventId, String eventName) {
        return !processedEvents.markProcessed(eventId, eventName);
    }

    private void appendIfPositive(UUID bookingId, java.time.Instant occurredAt, LedgerEntryType type,
                                  UUID counterparty, Money amount) {
        if (amount == null || amount.isZero()) {
            return;
        }
        ledger.append(LedgerEntry.of(bookingId, type, counterparty, amount, occurredAt));
    }
}
