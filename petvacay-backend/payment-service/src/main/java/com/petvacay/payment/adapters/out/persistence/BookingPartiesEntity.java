package com.petvacay.payment.adapters.out.persistence;

import com.petvacay.payment.domain.model.BookingParties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    protected BookingPartiesEntity() {
    }

    public static BookingPartiesEntity fromDomain(BookingParties parties) {
        BookingPartiesEntity entity = new BookingPartiesEntity();
        entity.bookingId = parties.bookingId();
        entity.clientId = parties.clientId();
        entity.sitterId = parties.sitterId();
        return entity;
    }

    public BookingParties toDomain() {
        return new BookingParties(bookingId, clientId, sitterId);
    }
}
