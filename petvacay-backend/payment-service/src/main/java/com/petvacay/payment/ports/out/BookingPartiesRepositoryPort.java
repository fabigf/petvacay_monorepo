package com.petvacay.payment.ports.out;

import com.petvacay.payment.domain.model.BookingParties;

import java.util.Optional;
import java.util.UUID;

/** Proyeccion local de las partes de cada reserva, alimentada por eventos. */
public interface BookingPartiesRepositoryPort {

    void save(BookingParties parties);

    Optional<BookingParties> findByBookingId(UUID bookingId);
}
