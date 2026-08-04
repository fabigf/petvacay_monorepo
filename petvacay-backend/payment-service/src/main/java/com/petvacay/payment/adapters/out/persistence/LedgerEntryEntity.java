package com.petvacay.payment.adapters.out.persistence;

import com.petvacay.payment.domain.model.LedgerEntry;
import com.petvacay.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntryEntity {

    @Id
    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerEntry.LedgerEntryType type;

    @Column(name = "counterparty_id")
    private UUID counterpartyId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Referencia del PaymentIntent / Transfer de Stripe (se rellenara en Fase 6). */
    @Column(name = "external_reference")
    private String externalReference;

    protected LedgerEntryEntity() {
    }

    public static LedgerEntryEntity fromDomain(LedgerEntry entry) {
        LedgerEntryEntity entity = new LedgerEntryEntity();
        entity.entryId = entry.entryId();
        entity.bookingId = entry.bookingId();
        entity.type = entry.type();
        entity.counterpartyId = entry.counterpartyId();
        entity.amount = entry.amount().amount();
        entity.currency = entry.amount().currency().getCurrencyCode();
        entity.occurredAt = entry.occurredAt();
        entity.externalReference = entry.externalReference();
        return entity;
    }

    public LedgerEntry toDomain() {
        return new LedgerEntry(entryId, bookingId, type, counterpartyId,
                new Money(amount, Currency.getInstance(currency)), occurredAt, externalReference);
    }
}
