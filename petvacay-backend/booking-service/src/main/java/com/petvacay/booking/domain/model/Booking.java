package com.petvacay.booking.domain.model;

import com.petvacay.booking.domain.event.BookingEvents;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import com.petvacay.shared.domain.DateRange;
import com.petvacay.shared.domain.DomainEvent;
import com.petvacay.shared.domain.Money;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado raiz de una reserva. Contiene la maquina de estados completa descrita
 * en negocio.md seccion 4.2 y es Java puro: no depende de Spring, JPA ni Kafka.
 *
 * <p>Toda transicion invalida lanza {@link BusinessRuleViolationException} con un
 * {@code code} estable que el frontend usa para renderizar el error.
 */
public class Booking {

    /** Plazo maximo de respuesta del cuidador a una solicitud. */
    public static final Duration REQUEST_EXPIRY = Duration.ofHours(24);

    /** Ventana post-servicio antes de liberar los fondos del escrow. */
    public static final Duration PAYOUT_HOLD_WINDOW = Duration.ofHours(24);

    private final UUID id;
    private final UUID clientId;
    private final UUID sitterId;
    private final List<UUID> petIds;
    private final DateRange dates;
    private final ServiceType serviceType;
    private final Money pricePerNight;
    private final PricingBreakdown pricing;

    private BookingStatus status;
    private Instant requestedAt;
    private Instant confirmedAt;
    private Instant closedAt;
    private CancellationActor cancelledBy;
    private CancellationOutcome cancellationOutcome;
    private String disputeReason;
    private DisputeResolution disputeResolution;
    private String disputeResolutionNotes;
    private UUID disputeResolvedBy;

    private final transient List<DomainEvent> pendingEvents = new ArrayList<>();

    private Booking(UUID id, UUID clientId, UUID sitterId, List<UUID> petIds, DateRange dates,
                    ServiceType serviceType, Money pricePerNight, PricingBreakdown pricing,
                    BookingStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.sitterId = Objects.requireNonNull(sitterId, "sitterId");
        this.petIds = List.copyOf(Objects.requireNonNull(petIds, "petIds"));
        this.dates = Objects.requireNonNull(dates, "dates");
        this.serviceType = Objects.requireNonNull(serviceType, "serviceType");
        this.pricePerNight = Objects.requireNonNull(pricePerNight, "pricePerNight");
        this.pricing = Objects.requireNonNull(pricing, "pricing");
        this.status = Objects.requireNonNull(status, "status");
    }

    /**
     * Crea una reserva en estado {@code BORRADOR} calculando su desglose economico.
     * Es el unico punto de entrada de creacion: el precio nunca llega desde el cliente.
     */
    public static Booking draft(UUID id, UUID clientId, UUID sitterId, List<UUID> petIds,
                                DateRange dates, ServiceType serviceType, Money pricePerNight) {
        if (petIds == null || petIds.isEmpty()) {
            throw BusinessRuleViolationException.unprocessable(
                    "ERR_BOOKING_NO_PETS", "Reserva sin mascotas",
                    "Debes seleccionar al menos una mascota para la reserva.");
        }
        PricingBreakdown pricing = PricingPolicy.quote(pricePerNight, dates.nights(), petIds.size());
        return new Booking(id, clientId, sitterId, petIds, dates, serviceType, pricePerNight,
                pricing, BookingStatus.BORRADOR);
    }

    /** Rehidratacion desde el adaptador de persistencia; no emite eventos. */
    public static Booking rehydrate(UUID id, UUID clientId, UUID sitterId, List<UUID> petIds,
                                    DateRange dates, ServiceType serviceType, Money pricePerNight,
                                    PricingBreakdown pricing, BookingStatus status,
                                    Instant requestedAt, Instant confirmedAt, Instant closedAt,
                                    CancellationActor cancelledBy, CancellationOutcome cancellationOutcome,
                                    String disputeReason, DisputeResolution disputeResolution,
                                    String disputeResolutionNotes, UUID disputeResolvedBy) {
        Booking booking = new Booking(id, clientId, sitterId, petIds, dates, serviceType,
                pricePerNight, pricing, status);
        booking.requestedAt = requestedAt;
        booking.confirmedAt = confirmedAt;
        booking.closedAt = closedAt;
        booking.cancelledBy = cancelledBy;
        booking.cancellationOutcome = cancellationOutcome;
        booking.disputeReason = disputeReason;
        booking.disputeResolution = disputeResolution;
        booking.disputeResolutionNotes = disputeResolutionNotes;
        booking.disputeResolvedBy = disputeResolvedBy;
        return booking;
    }

    // ---------------------------------------------------------------- transiciones

    /** BORRADOR -> SOLICITADO. El cuidador recibe la propuesta. */
    public void request(Instant now) {
        requireStatus(now, BookingStatus.BORRADOR);
        if (!dates.start().atStartOfDay().toInstant(ZoneOffset.UTC).isAfter(now)) {
            throw BusinessRuleViolationException.unprocessable(
                    "ERR_BOOKING_START_IN_PAST", "Fechas inválidas",
                    "La fecha de inicio del servicio debe ser futura.");
        }
        this.status = BookingStatus.SOLICITADO;
        this.requestedAt = now;
        raise(new BookingEvents.ReservaSolicitadaEvent(UUID.randomUUID(), now, id, clientId,
                sitterId, dates, pricing.totalChargedToClient()));
    }

    /** SOLICITADO -> PENDIENTE_PAGO. Rechaza la operacion si la solicitud ya expiro. */
    public void acceptBySitter(Instant now) {
        requireStatus(now, BookingStatus.SOLICITADO);
        if (isRequestExpired(now)) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_BOOKING_REQUEST_EXPIRED", "Solicitud expirada",
                    "La solicitud superó el plazo de 24 horas de respuesta y ya no puede aceptarse.");
        }
        this.status = BookingStatus.PENDIENTE_PAGO;
        raise(new BookingEvents.ReservaAceptadaEvent(UUID.randomUUID(), now, id, clientId, sitterId,
                pricing.totalChargedToClient()));
    }

    /** SOLICITADO -> CANCELADO por decision explicita del cuidador. */
    public void rejectBySitter(Instant now) {
        requireStatus(now, BookingStatus.SOLICITADO);
        cancel(CancellationActor.SITTER, now);
    }

    /** SOLICITADO -> CANCELADO por el temporizador de 24h. */
    public void expireRequest(Instant now) {
        requireStatus(now, BookingStatus.SOLICITADO);
        if (!isRequestExpired(now)) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_BOOKING_NOT_EXPIRED", "Solicitud vigente",
                    "La solicitud todavía está dentro del plazo de respuesta de 24 horas.");
        }
        cancel(CancellationActor.SYSTEM, now);
    }

    /**
     * PENDIENTE_PAGO -> CONFIRMADO. A partir de aqui los fondos estan en custodia
     * y el calendario del cuidador queda bloqueado para estas fechas.
     */
    public void confirmPayment(Instant now) {
        requireStatus(now, BookingStatus.PENDIENTE_PAGO);
        this.status = BookingStatus.CONFIRMADO;
        this.confirmedAt = now;
        raise(new BookingEvents.PagoConfirmadoEvent(UUID.randomUUID(), now, id, clientId, sitterId,
                dates, petIds.size(), pricing.totalChargedToClient(), scheduledPayoutAt()));
    }

    /** PENDIENTE_PAGO -> CANCELADO por fallo de la pasarela. */
    public void failPayment(Instant now) {
        requireStatus(now, BookingStatus.PENDIENTE_PAGO);
        cancel(CancellationActor.SYSTEM, now);
    }

    /** CONFIRMADO -> EN_PROGRESO cuando llega la fecha de inicio. */
    public void start(Instant now) {
        requireStatus(now, BookingStatus.CONFIRMADO);
        if (now.isBefore(dates.start().atStartOfDay().toInstant(ZoneOffset.UTC))) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_BOOKING_NOT_STARTED", "Servicio no iniciado",
                    "El servicio todavía no ha alcanzado su fecha de inicio.");
        }
        this.status = BookingStatus.EN_PROGRESO;
    }

    /**
     * EN_PROGRESO -> FINALIZADO una vez superada la ventana de 24h sin disputa.
     * Emite el evento que autoriza la liquidacion de fondos.
     */
    public void complete(Instant now) {
        requireStatus(now, BookingStatus.EN_PROGRESO);
        if (now.isBefore(scheduledPayoutAt())) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_BOOKING_PAYOUT_HOLD", "Ventana de disputa activa",
                    "Los fondos no pueden liberarse hasta 24 horas después de finalizar el servicio.");
        }
        this.status = BookingStatus.FINALIZADO;
        this.closedAt = now;
        raise(new BookingEvents.ReservaFinalizadaEvent(UUID.randomUUID(), now, id, sitterId,
                dates, petIds.size(), pricing.sitterPayout(), pricing.platformRevenue()));
    }

    /**
     * Boton de alerta medica del cuidador (negocio.md 5). Solo tiene sentido con
     * la mascota ya bajo su cuidado, es decir con el servicio en curso o a punto
     * de empezar; no altera el estado de la reserva ni el escrow.
     */
    public void raisePetEmergency(String description, Instant now) {
        if (status != BookingStatus.CONFIRMADO && status != BookingStatus.EN_PROGRESO) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_EMERGENCY_NOT_IN_SERVICE", "Servicio no activo",
                    "Solo puede darse una alerta médica mientras la reserva está en curso.");
        }
        raise(new BookingEvents.MascotaEnPeligroEvent(UUID.randomUUID(), now, id, sitterId, clientId,
                petIds, description));
    }

    /** Congela la reserva y detiene la liberacion automatica de fondos. */
    public void openDispute(UUID openedBy, String reason, Instant now) {
        if (status != BookingStatus.CONFIRMADO && status != BookingStatus.EN_PROGRESO) {
            throw invalidTransition("EN_DISPUTA");
        }
        if (now.isAfter(scheduledPayoutAt())) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_DISPUTE_WINDOW_CLOSED", "Plazo de disputa cerrado",
                    "El plazo de 24 horas para abrir una disputa ya ha finalizado.");
        }
        this.status = BookingStatus.EN_DISPUTA;
        this.disputeReason = reason;
        raise(new BookingEvents.DisputaAbiertaEvent(UUID.randomUUID(), now, id, clientId, sitterId,
                openedBy, reason));
    }

    /**
     * EN_DISPUTA -> FINALIZADO por resolucion manual del equipo de administracion
     * (negocio.md 4.4). Es la unica salida de una disputa: sin ella la reserva se
     * queda congelada para siempre y el dinero, retenido en el escrow.
     *
     * <p>El estado final es FINALIZADO aunque se le devuelva todo al cliente: la
     * reserva llego a pagarse y a bloquear el calendario, asi que llamarla
     * CANCELADO falsearia su historia. Que dinero acabo en cada mano lo cuenta el
     * {@link CancellationOutcome}, no el estado.
     *
     * @param refundToClient importe que el administrador devuelve al cliente; la
     *                       resolucion en si viaja en el evento, no cambia el calculo.
     */
    public void resolveDispute(UUID resolvedBy, DisputeResolution resolution, Money refundToClient,
                               String notes, Instant now) {
        requireStatus(now, BookingStatus.EN_DISPUTA);
        CancellationOutcome outcome = CancellationPolicy.resolveDispute(this, refundToClient);
        this.status = BookingStatus.FINALIZADO;
        this.cancellationOutcome = outcome;
        this.closedAt = now;
        this.disputeResolution = resolution;
        this.disputeResolutionNotes = notes;
        this.disputeResolvedBy = resolvedBy;
        raise(new BookingEvents.DisputaResueltaEvent(UUID.randomUUID(), now, id, clientId, sitterId,
                dates, petIds.size(), resolvedBy, resolution, notes, outcome));
    }

    /** Cancelacion iniciada por cliente o cuidador; aplica la politica de reembolso. */
    public void cancel(CancellationActor actor, Instant now) {
        if (status.isTerminal()) {
            throw invalidTransition("CANCELADO");
        }
        if (status == BookingStatus.EN_DISPUTA) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_BOOKING_IN_DISPUTE", "Reserva en disputa",
                    "Una reserva en disputa solo puede resolverla el equipo de administración.");
        }
        CancellationOutcome outcome = CancellationPolicy.resolve(this, actor, now);
        this.status = BookingStatus.CANCELADO;
        this.cancelledBy = actor;
        this.cancellationOutcome = outcome;
        this.closedAt = now;
        raise(new BookingEvents.ReservaCanceladaEvent(UUID.randomUUID(), now, id, clientId,
                sitterId, dates, petIds.size(), actor, outcome));
    }

    // ---------------------------------------------------------------- consultas

    public boolean isRequestExpired(Instant now) {
        return requestedAt != null && Duration.between(requestedAt, now).compareTo(REQUEST_EXPIRY) > 0;
    }

    /** Instante a partir del cual el escrow puede liberarse (fin del servicio + 24h). */
    public Instant scheduledPayoutAt() {
        return dates.end().atStartOfDay().toInstant(ZoneOffset.UTC).plus(PAYOUT_HOLD_WINDOW);
    }

    /** Una reserva bloquea el calendario del cuidador desde que esta confirmada. */
    public boolean blocksCalendar() {
        return status.isFunded() && status != BookingStatus.FINALIZADO;
    }

    private void requireStatus(Instant now, BookingStatus expected) {
        Objects.requireNonNull(now, "now");
        if (status != expected) {
            throw invalidTransition(expected.name());
        }
    }

    private BusinessRuleViolationException invalidTransition(String target) {
        return BusinessRuleViolationException.conflict(
                "ERR_BOOKING_INVALID_TRANSITION", "Transición no permitida",
                "La reserva está en estado " + status + " y no admite la operacion solicitada (" + target + ").");
    }

    private void raise(DomainEvent event) {
        pendingEvents.add(event);
    }

    public List<DomainEvent> pullPendingEvents() {
        List<DomainEvent> copy = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return copy;
    }

    public List<DomainEvent> pendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    // ---------------------------------------------------------------- accessors

    public UUID id() {
        return id;
    }

    public UUID clientId() {
        return clientId;
    }

    public UUID sitterId() {
        return sitterId;
    }

    public List<UUID> petIds() {
        return petIds;
    }

    public DateRange dates() {
        return dates;
    }

    public ServiceType serviceType() {
        return serviceType;
    }

    public Money pricePerNight() {
        return pricePerNight;
    }

    public PricingBreakdown pricing() {
        return pricing;
    }

    public BookingStatus status() {
        return status;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public CancellationActor cancelledBy() {
        return cancelledBy;
    }

    public CancellationOutcome cancellationOutcome() {
        return cancellationOutcome;
    }

    public String disputeReason() {
        return disputeReason;
    }

    public DisputeResolution disputeResolution() {
        return disputeResolution;
    }

    public String disputeResolutionNotes() {
        return disputeResolutionNotes;
    }

    public UUID disputeResolvedBy() {
        return disputeResolvedBy;
    }
}
