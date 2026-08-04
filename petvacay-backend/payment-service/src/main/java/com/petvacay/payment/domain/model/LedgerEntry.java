package com.petvacay.payment.domain.model;

import com.petvacay.shared.domain.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Apunte del libro mayor del escrow. Cada movimiento de dinero derivado del ciclo
 * de vida de una reserva deja una linea inmutable, de modo que el saldo retenido y
 * el ingreso de la plataforma sean auditables (negocio.md seccion 3.2).
 *
 * <p>Fase 1: se registran los apuntes que Stripe Connect ejecutara realmente en
 * Fase 6. El modelo no cambia al integrar la pasarela: solo se anadira la
 * referencia externa del PaymentIntent / Transfer.
 */
public record LedgerEntry(
        UUID entryId,
        UUID bookingId,
        LedgerEntryType type,
        UUID counterpartyId,
        Money amount,
        Instant occurredAt,
        String externalReference) {

    public static LedgerEntry of(UUID bookingId, LedgerEntryType type, UUID counterpartyId,
                                 Money amount, Instant occurredAt) {
        return new LedgerEntry(UUID.randomUUID(), bookingId, type, counterpartyId, amount, occurredAt, null);
    }

    public enum LedgerEntryType {
        /** Cobro al cliente y retencion en custodia. */
        ESCROW_HOLD,
        /** Liberacion del 85% al cuidador. */
        SITTER_PAYOUT,
        /** Consolidacion del 15% + 5% como ingreso de la plataforma. */
        PLATFORM_REVENUE,
        /** Devolucion al cliente por cancelacion. */
        CLIENT_REFUND,
        /** Compensacion de la primera noche al cuidador en cancelacion tardia. */
        SITTER_COMPENSATION
    }
}
