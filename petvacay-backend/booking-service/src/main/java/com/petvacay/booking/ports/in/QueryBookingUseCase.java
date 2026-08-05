package com.petvacay.booking.ports.in;

import com.petvacay.booking.domain.model.Booking;
import com.petvacay.booking.domain.model.BookingStatus;

import java.util.List;
import java.util.UUID;

public interface QueryBookingUseCase {

    Booking getById(UUID bookingId);

    List<Booking> listByClient(UUID clientId);

    List<Booking> listBySitter(UUID sitterId);

    /**
     * Listado transversal por estado, para el back-office. Sin el, un
     * administrador solo podria abrir una reserva en disputa si ya conociera su
     * identificador de memoria.
     */
    List<Booking> listByStatus(BookingStatus status);
}
