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

    /** El cliente ha pagado: el importe integro queda retenido en custodia. */
    @Transactional
    public void onPagoConfirmado(BookingEvents.PagoConfirmadoEvent event) {
        if (isDuplicate(event.eventId(), event.eventName())) {
            return;
        }

        // Se guarda quien es cliente y quien cuidador: es lo que permitira despues
        // decidir quien tiene derecho a consultar este libro mayor, sin preguntar
        // a booking-service en cada lectura.
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

        appendIfPositive(event, LedgerEntryType.CLIENT_REFUND, event.clientId(), outcome.refundToClient());
        appendIfPositive(event, LedgerEntryType.SITTER_COMPENSATION, event.sitterId(), outcome.payoutToSitter());
        appendIfPositive(event, LedgerEntryType.PLATFORM_REVENUE, null, outcome.platformRevenue());
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

    private void appendIfPositive(BookingEvents.ReservaCanceladaEvent event, LedgerEntryType type,
                                  UUID counterparty, Money amount) {
        if (amount == null || amount.isZero()) {
            return;
        }
        ledger.append(LedgerEntry.of(event.bookingId(), type, counterparty, amount, event.occurredAt()));
    }
}
