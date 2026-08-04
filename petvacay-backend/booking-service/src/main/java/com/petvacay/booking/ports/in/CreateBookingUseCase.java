package com.petvacay.booking.ports.in;

import com.petvacay.booking.domain.model.Booking;
import com.petvacay.booking.domain.model.ServiceType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Caso de uso de entrada: el cliente solicita una reserva desde React. */
public interface CreateBookingUseCase {

    Booking create(CreateBookingCommand command);

    record CreateBookingCommand(
            UUID clientId,
            UUID sitterId,
            List<UUID> petIds,
            LocalDate startDate,
            LocalDate endDate,
            ServiceType serviceType) {
    }
}
