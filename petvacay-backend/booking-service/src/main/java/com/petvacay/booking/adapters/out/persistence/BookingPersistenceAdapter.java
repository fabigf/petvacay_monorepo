package com.petvacay.booking.adapters.out.persistence;

import com.petvacay.booking.domain.model.Booking;
import com.petvacay.booking.domain.model.BookingStatus;
import com.petvacay.booking.ports.out.BookingRepositoryPort;
import com.petvacay.shared.domain.DateRange;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class BookingPersistenceAdapter implements BookingRepositoryPort {

    /** Estados en los que la reserva ocupa plazas en el calendario del cuidador. */
    private static final Set<BookingStatus> CALENDAR_BLOCKING = EnumSet.of(
            BookingStatus.CONFIRMADO, BookingStatus.EN_PROGRESO, BookingStatus.EN_DISPUTA);

    private final BookingJpaRepository repository;

    public BookingPersistenceAdapter(BookingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Booking save(Booking booking) {
        return repository.save(BookingEntity.fromDomain(booking)).toDomain();
    }

    @Override
    public Optional<Booking> findById(UUID bookingId) {
        return repository.findById(bookingId).map(BookingEntity::toDomain);
    }

    @Override
    public List<Booking> findByClientId(UUID clientId) {
        return repository.findByClientIdOrderByStartDateDesc(clientId).stream()
                .map(BookingEntity::toDomain).toList();
    }

    @Override
    public List<Booking> findBySitterId(UUID sitterId) {
        return repository.findBySitterIdOrderByStartDateDesc(sitterId).stream()
                .map(BookingEntity::toDomain).toList();
    }

    @Override
    public List<Booking> findByStatus(BookingStatus status) {
        return repository.findByStatusOrderByStartDateDesc(status).stream()
                .map(BookingEntity::toDomain).toList();
    }

    @Override
    public List<Booking> findCalendarBlockingBookings(UUID sitterId, DateRange range) {
        return repository.findOverlapping(sitterId, range.start(), range.end(), CALENDAR_BLOCKING).stream()
                .map(BookingEntity::toDomain).toList();
    }

    @Override
    public List<Booking> findExpiredRequests(Instant now) {
        Instant threshold = now.minus(Booking.REQUEST_EXPIRY);
        return repository.findByStatusAndRequestedAtBefore(BookingStatus.SOLICITADO, threshold).stream()
                .map(BookingEntity::toDomain).toList();
    }

    @Override
    public List<Booking> findBookingsDueToStart(LocalDate today) {
        return repository.findByStatusAndStartDateLessThanEqual(BookingStatus.CONFIRMADO, today).stream()
                .map(BookingEntity::toDomain).toList();
    }

    /**
     * El filtro en SQL es por fecha de fin, deliberadamente mas ancho que la regla
     * real (fin + 24h): la frontera exacta la vuelve a comprobar el dominio en
     * {@code Booking.complete()}, que es donde vive. Una reserva EN_DISPUTA nunca
     * entra aqui porque el filtro exige EN_PROGRESO, que es justo lo que congela
     * la disputa.
     */
    @Override
    public List<Booking> findBookingsDueForPayout(Instant now) {
        LocalDate endedBefore = LocalDate.ofInstant(now.minus(Booking.PAYOUT_HOLD_WINDOW), ZoneOffset.UTC);
        return repository.findByStatusAndEndDateLessThanEqual(BookingStatus.EN_PROGRESO, endedBefore).stream()
                .map(BookingEntity::toDomain)
                .filter(booking -> !now.isBefore(booking.scheduledPayoutAt()))
                .toList();
    }
}
