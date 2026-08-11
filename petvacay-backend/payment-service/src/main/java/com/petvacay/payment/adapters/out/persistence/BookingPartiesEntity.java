package com.petvacay.payment.adapters.out.persistence;

import com.petvacay.payment.domain.model.BookingParties;
import com.petvacay.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "booking_parties")
public class BookingPartiesEntity {

    @Id
    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "sitter_id", nullable = false)
    private UUID sitterId;

    /**
     * Anulables porque las filas escritas antes de V4 no lo tienen. Quien las
     * lea debe tratar el nulo como "importe desconocido" y negarse a cobrar, no
     * como cero.
     */
    @Column(name = "amount_to_pay")
    private BigDecimal amountToPay;

    @Column(name = "currency", length = 3)
    private String currency;

    protected BookingPartiesEntity() {
    }

    public static BookingPartiesEntity fromDomain(BookingParties parties) {
        BookingPartiesEntity entity = new BookingPartiesEntity();
        entity.bookingId = parties.bookingId();
        entity.clientId = parties.clientId();
        entity.sitterId = parties.sitterId();
        if (parties.amountToPay() != null) {
            entity.amountToPay = parties.amountToPay().amount();
            entity.currency = parties.amountToPay().currency().getCurrencyCode();
        }
        return entity;
    }

    public BookingParties toDomain() {
        Money amount = amountToPay == null || currency == null
                ? null
                : new Money(amountToPay, Currency.getInstance(currency));
        return new BookingParties(bookingId, clientId, sitterId, amount);
    }
}
