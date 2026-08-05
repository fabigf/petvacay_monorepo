package com.petvacay.booking.application;

import com.petvacay.booking.domain.model.Booking;
import com.petvacay.booking.domain.model.BookingStatus;
import com.petvacay.booking.domain.model.CancellationActor;
import com.petvacay.booking.ports.in.CreateBookingUseCase;
import com.petvacay.booking.ports.in.ManageBookingUseCase;
import com.petvacay.booking.ports.in.QueryBookingUseCase;
import com.petvacay.booking.ports.in.ReviewUseCase;
import com.petvacay.booking.ports.out.BookingEventPublisherPort;
import com.petvacay.booking.ports.out.BookingRepositoryPort;
import com.petvacay.booking.ports.out.ClientPetsPort;
import com.petvacay.booking.ports.out.SitterCatalogPort;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import com.petvacay.shared.domain.DateRange;
import com.petvacay.shared.domain.DomainEvent;
import com.petvacay.shared.domain.Money;
import com.petvacay.shared.domain.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Orquestador de los casos de uso de reserva. No contiene reglas de negocio:
 * carga el agregado, delega la decision en el dominio, persiste y publica eventos.
 */
@Service
public class BookingApplicationService implements CreateBookingUseCase, ManageBookingUseCase, QueryBookingUseCase {

    private static final Logger log = LoggerFactory.getLogger(BookingApplicationService.class);

    private final BookingRepositoryPort repository;
    private final SitterCatalogPort sitterCatalog;
    private final ClientPetsPort clientPets;
    private final ReviewUseCase reviews;
    private final NotificationApplicationService notifier;
    private final BookingEventPublisherPort eventPublisher;
    private final Clock clock;

    public BookingApplicationService(BookingRepositoryPort repository,
                                     SitterCatalogPort sitterCatalog,
                                     ClientPetsPort clientPets,
                                     ReviewUseCase reviews,
                                     NotificationApplicationService notifier,
                                     BookingEventPublisherPort eventPublisher,
                                     Clock clock) {
        this.reviews = reviews;
        this.notifier = notifier;
        this.repository = repository;
        this.sitterCatalog = sitterCatalog;
        this.clientPets = clientPets;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Booking create(CreateBookingCommand command) {
        DateRange dates = DateRange.of(command.startDate(), command.endDate());

        assertPetsBelongToClient(command.clientId(), command.petIds());

        SitterCatalogPort.SitterSummary sitter = sitterCatalog.findSitter(command.sitterId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ERR_SITTER_NOT_FOUND", "No existe el cuidador " + command.sitterId()));

        if (!sitter.verified()) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_SITTER_NOT_VERIFIED", "Cuidador no verificado",
                    "El cuidador seleccionado aún no ha superado el proceso de verificación de identidad.");
        }

        Money price = sitter.servicePrices().get(command.serviceType());
        if (price == null) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_SERVICE_NOT_OFFERED", "Servicio no ofrecido",
                    "Este cuidador no ofrece el servicio solicitado.");
        }

        assertCapacityAvailable(sitter, dates, command.petIds().size());

        Booking booking = Booking.draft(UUID.randomUUID(), command.clientId(), command.sitterId(),
                command.petIds(), dates, command.serviceType(), price);
        booking.request(now());

        return persist(booking);
    }

    /**
     * Comprueba que todas las mascotas de la solicitud son del cliente.
     *
     * <p>Los identificadores de mascota son UUID, no secretos: sin esta
     * comprobacion, conocer el de la mascota de otra persona bastaba para
     * incluirla en una reserva propia — y con ella su ficha medica, su nombre y
     * el compromiso de entregarsela a un cuidador desconocido.
     */
    private void assertPetsBelongToClient(UUID clientId, List<UUID> petIds) {
        Set<UUID> owned = clientPets.petsOwnedBy(clientId);
        if (!owned.containsAll(petIds)) {
            throw new BusinessRuleViolationException("ERR_PET_NOT_OWNED", "Mascota ajena", 403,
                    "Solo puedes reservar para mascotas registradas en tu propio perfil.");
        }
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
        Booking saved = persist(booking);

        // negocio.md 4.3: cancelar una reserva ya confirmada le cuesta al cuidador
        // una resena negativa del sistema, que a su vez baja su reputacion — y con
        // ella su posicion — en el buscador. El propio caso de uso decide si
        // procede; aqui solo se le da la oportunidad de hacerlo.
        reviews.penalizeSitterCancellation(bookingId);

        return saved;
    }

    @Override
    @Transactional
    public Booking openDispute(UUID bookingId, UUID actingUserId, String reason) {
        return mutate(bookingId, actingUserId, null,
                booking -> booking.openDispute(actingUserId, reason, now()));
    }

    /**
     * Resolucion manual de una disputa. La cuantia devuelta al cliente solo se
     * acepta del administrador cuando la resolucion es {@code SPLIT}: en los dos
     * extremos la deduce el propio servicio del importe real de la reserva, para
     * que "a favor del cliente" no pueda significar accidentalmente un reembolso
     * distinto de lo que se le cobro.
     */
    @Override
    @Transactional
    public Booking resolveDispute(ResolveDisputeCommand command) {
        Booking booking = load(command.bookingId());
        Money total = booking.pricing().totalChargedToClient();

        Money refund = switch (command.resolution()) {
            case FAVOR_CLIENT -> total;
            case FAVOR_SITTER -> Money.zero(total.currency());
            case SPLIT -> {
                if (command.refundToClient() == null) {
                    throw BusinessRuleViolationException.unprocessable(
                            "ERR_DISPUTE_REFUND_REQUIRED", "Reembolso obligatorio",
                            "Una resolución intermedia debe indicar cuánto se le devuelve al cliente.");
                }
                yield new Money(command.refundToClient(), total.currency());
            }
        };

        booking.resolveDispute(command.resolvedBy(), command.resolution(), refund, command.notes(), now());
        return persist(booking);
    }

    @Override
    @Transactional
    public Booking raisePetEmergency(UUID bookingId, UUID actingUserId, String description) {
        return mutate(bookingId, actingUserId, CancellationActor.SITTER,
                booking -> booking.raisePetEmergency(description, now()));
    }

    @Override
    @Transactional
    public int expireStaleRequests() {
        Instant now = now();
        return sweep(repository.findExpiredRequests(now), booking -> booking.expireRequest(now));
    }

    @Override
    @Transactional
    public int startDueBookings() {
        Instant now = now();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        return sweep(repository.findBookingsDueToStart(today), booking -> booking.start(now));
    }

    @Override
    @Transactional
    public int releaseDuePayouts() {
        Instant now = now();
        return sweep(repository.findBookingsDueForPayout(now), booking -> booking.complete(now));
    }

    /**
     * Aplica una transicion automatica a un lote y devuelve cuantas prosperaron.
     *
     * <p>Una reserva que el dominio rechace (por ejemplo, porque una disputa la
     * congelo entre la consulta y la transicion) se registra y se salta: un caso
     * borde no puede dejar sin liquidar al resto de cuidadores del barrido.
     */
    private int sweep(List<Booking> candidates, Consumer<Booking> transition) {
        int applied = 0;
        for (Booking booking : candidates) {
            try {
                transition.accept(booking);
                persist(booking);
                applied++;
            } catch (BusinessRuleViolationException e) {
                log.warn("Transicion automatica descartada para la reserva {}: {}", booking.id(), e.getMessage());
            }
        }
        return applied;
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

    @Override
    @Transactional(readOnly = true)
    public List<Booking> listByStatus(BookingStatus status) {
        return repository.findByStatus(status);
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

    /**
     * Guarda el agregado y reparte sus eventos: al buzon de avisos de la app —
     * dentro de esta misma transaccion, para que no quede constancia de algo que
     * no llego a guardarse — y al bus de integracion.
     */
    private Booking persist(Booking booking) {
        Booking saved = repository.save(booking);
        List<DomainEvent> events = booking.pullPendingEvents();
        notifier.recordAll(events);
        eventPublisher.publish(events);
        return saved;
    }

    private Instant now() {
        return clock.instant();
    }
}
