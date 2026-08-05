package com.petvacay.booking.ports.out;

import com.petvacay.booking.domain.model.Booking;
import com.petvacay.shared.domain.DateRange;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SPI de persistencia del agregado Booking. */
public interface BookingRepositoryPort {

    Booking save(Booking booking);

    Optional<Booking> findById(UUID bookingId);

    List<Booking> findByClientId(UUID clientId);

    List<Booking> findBySitterId(UUID sitterId);

    /** Listado transversal por estado; lo consume el back-office. */
    List<Booking> findByStatus(com.petvacay.booking.domain.model.BookingStatus status);

    /**
     * Reservas del cuidador que bloquean calendario (estados con fondos en custodia)
     * y solapan con el rango indicado. Base del control de capacidad simultanea.
     */
    List<Booking> findCalendarBlockingBookings(UUID sitterId, DateRange range);

    /** Solicitudes en estado SOLICITADO cuya ventana de 24h ya vencio. */
    List<Booking> findExpiredRequests(java.time.Instant now);

    /** Reservas CONFIRMADO cuya fecha de inicio ya ha llegado: procede EN_PROGRESO. */
    List<Booking> findBookingsDueToStart(java.time.LocalDate today);

    /**
     * Reservas EN_PROGRESO cuyo servicio termino hace mas de la ventana de
     * retencion del escrow y que nadie ha disputado: procede FINALIZADO y con el
     * la liquidacion al cuidador.
     */
    List<Booking> findBookingsDueForPayout(java.time.Instant now);
}
