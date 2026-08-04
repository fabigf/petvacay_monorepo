package com.petvacay.booking.ports.in;

import com.petvacay.booking.domain.model.Booking;

import java.util.List;
import java.util.UUID;

public interface QueryBookingUseCase {

    Booking getById(UUID bookingId);

    List<Booking> listByClient(UUID clientId);

    List<Booking> listBySitter(UUID sitterId);
}
