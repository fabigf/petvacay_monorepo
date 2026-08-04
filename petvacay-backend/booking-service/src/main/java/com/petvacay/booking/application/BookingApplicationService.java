package com.petvacay.booking.application;

import com.petvacay.booking.domain.model.Booking;
import com.petvacay.booking.domain.model.CancellationActor;
import com.petvacay.booking.ports.in.CreateBookingUseCase;
import com.petvacay.booking.ports.in.ManageBookingUseCase;
import com.petvacay.booking.ports.in.QueryBookingUseCase;
import com.petvacay.booking.ports.out.BookingEventPublisherPort;
import com.petvacay.booking.ports.out.BookingRepositoryPort;
import com.petvacay.booking.ports.out.SitterCatalogPort;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import com.petvacay.shared.domain.DateRange;
import com.petvacay.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Orquestador de los casos de uso de reserva. No contiene reglas de negocio:
 * carga el agregado, delega la decision en el dominio, persiste y publica eventos.
 */
@Service
public class BookingApplicationService implements CreateBookingUseCase, ManageBookingUseCase, QueryBookingUseCase {

    private final BookingRepositoryPort repository;
    private final SitterCatalogPort sitterCatalog;
    private final BookingEventPublisherPort eventPublisher;
    private final Clock clock;

    public BookingApplicationService(BookingRepositoryPort repository,
                                     SitterCatalogPort sitterCatalog,
                                     BookingEventPublisherPort eventPublisher,
                                     Clock clock) {
        this.repository = repository;
        this.sitterCatalog = sitterCatalog;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Booking create(CreateBookingCommand command) {
        DateRange dates = DateRange.of(command.startDate(), command.endDate());

        SitterCatalogPort.SitterSummary sitter = sitterCatalog.findSitter(command.sitterId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ERR_SITTER_NOT_FOUND", "No existe el cuidador " + command.sitterId()));

        if (!sitter.verified()) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_SITTER_NOT_VERIFIED", "Cuidador no verificado",
                    "El cuidador seleccionado aún no ha superado el proceso de verificación de identidad.");
        }

        assertCapacityAvailable(sitter, dates, command.petIds().size());

        Booking booking = Booking.draft(UUID.randomUUID(), command.clientId(), command.sitterId(),
                command.petIds(), dates, command.serviceType(), sitter.pricePerNight());
        booking.request(now());

        return persist(booking);
    }

    /**
     * Verifica noche a noche que la capacidad maxima de mascotas simultaneas del
     * cuidador no se supere (negocio.md seccion 4.2, bloqueo de calendario).
     */
    private void assertCapacityAvailable(SitterCatalogPort.SitterSummary sitter, DateRange dates, int petCount) {
        List<Booking> blocking = repository.findCalendarBlockingBookings(sitter.sitterId(), dates);

        for (LocalDate night = dates.start(); night.isBefore(dates.end()); night = night.plusDays(1)) {
            final LocalDate current = night;
            int occupied = blocking.stream()
                    .filter(b -> b.dates().contains(current))
                    .mapToInt(b -> b.petIds().size())
                    .sum();

            if (occupied + petCount > sitter.maxSimultaneousPets()) {
                throw BusinessRuleViolationException.conflict(
                        "ERR_BOOKING_DATE_CONFLICT", "Cuidador No Disponible",
                        "El cuidador seleccionado ya dispone de una reserva confirmada para esas fechas.");
            }
        }
    }

    @Override
    @Transactional
    public Booking acceptBySitter(UUID bookingId, UUID actingUserId) {
        return mutate(bookingId, actingUserId, CancellationActor.SITTER,
                booking -> booking.acceptBySitter(now()));
    }

    @Override
    @Transactional
    public Booking rejectBySitter(UUID bookingId, UUID actingUserId) {
        return mutate(bookingId, actingUserId, CancellationActor.SITTER,
                booking -> booking.rejectBySitter(now()));
    }

    @Override
    @Transactional
    public Booking confirmPayment(UUID bookingId, UUID actingUserId) {
        return mutate(bookingId, actingUserId, CancellationActor.CLIENT,
                booking -> booking.confirmPayment(now()));
    }

    @Override
    @Transactional
    public Booking failPayment(UUID bookingId, UUID actingUserId) {
        return mutate(bookingId, actingUserId, CancellationActor.CLIENT,
                booking -> booking.failPayment(now()));
    }

    @Override
    @Transactional
    public Booking start(UUID bookingId, UUID actingUserId) {
        return mutate(bookingId, actingUserId, null, booking -> booking.start(now()));
    }

    @Override
    @Transactional
    public Booking complete(UUID bookingId, UUID actingUserId) {
        return mutate(bookingId, actingUserId, null, booking -> booking.complete(now()));
    }

    /**
     * El actor no se recibe: se deduce de si quien cancela es el cliente o el
     * cuidador de esta reserva concreta. De ello depende la politica de reembolso,
     * asi que dejarlo en manos del cliente equivaldria a dejarle elegir cuanto
     * dinero recupera.
     */
    @Override
    @Transactional
    public Booking cancel(UUID bookingId, UUID actingUserId) {
        Booking booking = load(bookingId);
        CancellationActor actor = requireParticipant(booking, actingUserId, null);
        booking.cancel(actor, now());
        return persist(booking);
    }

    @Override
    @Transactional
    public Booking openDispute(UUID bookingId, UUID actingUserId, String reason) {
        return mutate(bookingId, actingUserId, null,
                booking -> booking.openDispute(actingUserId, reason, now()));
    }

    @Override
    @Transactional
    public int expireStaleRequests() {
        Instant now = now();
        List<Booking> expired = repository.findExpiredRequests(now);
        expired.forEach(booking -> {
            booking.expireRequest(now);
            persist(booking);
        });
        return expired.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Booking getById(UUID bookingId) {
        return load(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> listByClient(UUID clientId) {
        return repository.findByClientId(clientId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> listBySitter(UUID sitterId) {
        return repository.findBySitterId(sitterId);
    }

    private Booking mutate(UUID bookingId, UUID actingUserId, CancellationActor requiredRole,
                           Consumer<Booking> transition) {
        Booking booking = load(bookingId);
        requireParticipant(booking, actingUserId, requiredRole);
        transition.accept(booking);
        return persist(booking);
    }

    /**
     * Comprueba que el usuario forma parte de la reserva y devuelve con que rol.
     *
     * <p>Sin esto, conocer un identificador de reserva bastaria para que cualquier
     * usuario autenticado la aceptara, la cancelara o la marcara como pagada: los
     * identificadores son UUID, no secretos, y pueden acabar en un log, en la
     * barra de direcciones o en una captura de pantalla.
     *
     * @param requiredRole rol exigido para la operacion, o {@code null} si vale
     *                     cualquiera de las dos partes (cancelar, abrir disputa).
     */
    private CancellationActor requireParticipant(Booking booking, UUID actingUserId,
                                                 CancellationActor requiredRole) {
        CancellationActor role;
        if (booking.clientId().equals(actingUserId)) {
            role = CancellationActor.CLIENT;
        } else if (booking.sitterId().equals(actingUserId)) {
            role = CancellationActor.SITTER;
        } else {
            throw new BusinessRuleViolationException("ERR_NOT_BOOKING_PARTICIPANT", "Acceso denegado",
                    403, "Esta reserva no te pertenece.");
        }

        if (requiredRole != null && role != requiredRole) {
            throw new BusinessRuleViolationException("ERR_WRONG_BOOKING_ROLE", "Acceso denegado", 403,
                    requiredRole == CancellationActor.SITTER
                            ? "Solo el cuidador de la reserva puede realizar esta acción."
                            : "Solo el cliente de la reserva puede realizar esta acción.");
        }
        return role;
    }

    private Booking load(UUID bookingId) {
        return repository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ERR_BOOKING_NOT_FOUND", "No existe la reserva " + bookingId));
    }

    private Booking persist(Booking booking) {
        Booking saved = repository.save(booking);
        eventPublisher.publish(booking.pullPendingEvents());
        return saved;
    }

    private Instant now() {
        return clock.instant();
    }
}
