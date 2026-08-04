package com.petvacay.booking.application;

import com.petvacay.booking.domain.event.BookingEvents;
import com.petvacay.booking.domain.model.Booking;
import com.petvacay.booking.domain.model.BookingStatus;
import com.petvacay.booking.domain.model.Review;
import com.petvacay.booking.domain.model.ReviewAuthorRole;
import com.petvacay.booking.ports.in.ReviewUseCase;
import com.petvacay.booking.ports.out.BookingEventPublisherPort;
import com.petvacay.booking.ports.out.BookingRepositoryPort;
import com.petvacay.booking.ports.out.ReviewRepositoryPort;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import com.petvacay.shared.domain.DomainEvent;
import com.petvacay.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orquestador de resenas. Vive separado de {@link BookingApplicationService} a
 * proposito: opera sobre un agregado propio (Review) y solo LEE Booking para
 * comprobar estado y pertenencia, no lo modifica — mezclarlo en el mismo
 * servicio confundiria dos responsabilidades distintas.
 */
@Service
public class ReviewApplicationService implements ReviewUseCase {

    private final BookingRepositoryPort bookings;
    private final ReviewRepositoryPort reviews;
    private final BookingEventPublisherPort eventPublisher;
    private final Clock clock;

    public ReviewApplicationService(BookingRepositoryPort bookings, ReviewRepositoryPort reviews,
                                    BookingEventPublisherPort eventPublisher, Clock clock) {
        this.bookings = bookings;
        this.reviews = reviews;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Review leaveReview(UUID bookingId, UUID authorId, int rating, String comment) {
        Booking booking = loadBooking(bookingId);

        if (booking.status() != BookingStatus.FINALIZADO) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_REVIEW_BOOKING_NOT_FINISHED", "Reserva no finalizada",
                    "Solo se puede valorar una reserva una vez ha finalizado el servicio.");
        }

        ReviewAuthorRole role;
        UUID subjectId;
        if (booking.clientId().equals(authorId)) {
            role = ReviewAuthorRole.CLIENT;
            subjectId = booking.sitterId();
        } else if (booking.sitterId().equals(authorId)) {
            role = ReviewAuthorRole.SITTER;
            subjectId = booking.clientId();
        } else {
            throw new BusinessRuleViolationException("ERR_NOT_BOOKING_PARTICIPANT", "Acceso denegado", 403,
                    "Esta reserva no te pertenece.");
        }

        if (reviews.existsByBookingIdAndAuthorRole(bookingId, role)) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_REVIEW_ALREADY_EXISTS", "Ya has valorado esta reserva",
                    "Solo se puede dejar una valoración por reserva.");
        }

        Review review = Review.create(bookingId, authorId, subjectId, role, rating, comment, now());
        Review saved = reviews.save(review);

        // Solo la resena del CLIENTE hacia el cuidador tiene una vitrina publica
        // (el buscador). La del cuidador hacia el cliente se guarda igual, pero
        // ningun modelo de lectura la consume todavia: publicar ese evento no
        // tendria ningun oyente.
        if (role == ReviewAuthorRole.CLIENT) {
            publish(new BookingEvents.ResenaCreadaEvent(
                    UUID.randomUUID(), now(), bookingId, saved.id(), subjectId, rating));
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Review> listForBooking(UUID bookingId, UUID actingUserId) {
        Booking booking = loadBooking(bookingId);
        if (!booking.clientId().equals(actingUserId) && !booking.sitterId().equals(actingUserId)) {
            throw new BusinessRuleViolationException("ERR_NOT_BOOKING_PARTICIPANT", "Acceso denegado", 403,
                    "Esta reserva no te pertenece.");
        }
        return reviews.findByBookingId(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Review> listForSitter(UUID sitterId) {
        return reviews.findBySubjectIdOrderByCreatedAtDesc(sitterId);
    }

    private Booking loadBooking(UUID bookingId) {
        return bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ERR_BOOKING_NOT_FOUND", "No existe la reserva " + bookingId));
    }

    private void publish(DomainEvent event) {
        eventPublisher.publish(List.of(event));
    }

    private Instant now() {
        return clock.instant();
    }
}
