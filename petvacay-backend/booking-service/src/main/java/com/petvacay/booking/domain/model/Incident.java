package com.petvacay.booking.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Incidencia registrada sobre una reserva: una disputa o una alerta medica.
 *
 * <p>Existe como agregado propio y no como un par de campos mas en
 * {@link Booking} porque responde a otra pregunta. La reserva contesta "¿en que
 * estado esta este servicio?"; la incidencia, "¿que le ha pasado a esta reserva
 * y quien tiene que atenderlo?". Una misma reserva puede acumular varias — dos
 * alertas medicas en dias distintos son dos hechos, no uno que se sobrescribe —
 * y el equipo de administracion necesita verlas todas, no solo la ultima.
 *
 * <p>Lleva copia de {@code clientId} y {@code sitterId} en vez de obligar a
 * cruzar con la reserva: el back-office lista incidencias de muchas reservas a
 * la vez y necesita saber a quien afecta cada una sin una consulta por fila.
 */
public final class Incident {

    private final UUID id;
    private final UUID bookingId;
    private final UUID clientId;
    private final UUID sitterId;
    private final IncidentType type;
    private final String description;
    private final Instant openedAt;
    private final UUID openedBy;

    private IncidentStatus status;
    private String resolutionNotes;
    private Instant resolvedAt;
    private UUID resolvedBy;

    private Incident(UUID id, UUID bookingId, UUID clientId, UUID sitterId, IncidentType type,
                     String description, Instant openedAt, UUID openedBy, IncidentStatus status,
                     String resolutionNotes, Instant resolvedAt, UUID resolvedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.sitterId = Objects.requireNonNull(sitterId, "sitterId");
        this.type = Objects.requireNonNull(type, "type");
        this.description = description;
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        this.openedBy = openedBy;
        this.status = Objects.requireNonNull(status, "status");
        this.resolutionNotes = resolutionNotes;
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
    }

    public static Incident open(UUID bookingId, UUID clientId, UUID sitterId, IncidentType type,
                                String description, UUID openedBy, Instant now) {
        return new Incident(UUID.randomUUID(), bookingId, clientId, sitterId, type, description,
                now, openedBy, IncidentStatus.ABIERTA, null, null, null);
    }

    public static Incident rehydrate(UUID id, UUID bookingId, UUID clientId, UUID sitterId,
                                     IncidentType type, String description, Instant openedAt,
                                     UUID openedBy, IncidentStatus status, String resolutionNotes,
                                     Instant resolvedAt, UUID resolvedBy) {
        return new Incident(id, bookingId, clientId, sitterId, type, description, openedAt, openedBy,
                status, resolutionNotes, resolvedAt, resolvedBy);
    }

    /**
     * Cierra la incidencia dejando constancia de quien y por que.
     *
     * <p>Es idempotente: cerrar una ya cerrada no la reescribe. Una disputa se
     * cierra sola al resolverla el administrador, y ese cierre puede llegar por
     * el mismo camino mas de una vez si el evento se reentrega.
     */
    public void close(String notes, UUID closedBy, Instant now) {
        if (status == IncidentStatus.RESUELTA) {
            return;
        }
        this.status = IncidentStatus.RESUELTA;
        this.resolutionNotes = notes;
        this.resolvedAt = now;
        this.resolvedBy = closedBy;
    }

    public boolean isOpen() {
        return status == IncidentStatus.ABIERTA;
    }

    public UUID id() {
        return id;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public UUID clientId() {
        return clientId;
    }

    public UUID sitterId() {
        return sitterId;
    }

    public IncidentType type() {
        return type;
    }

    public String description() {
        return description;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public UUID openedBy() {
        return openedBy;
    }

    public IncidentStatus status() {
        return status;
    }

    public String resolutionNotes() {
        return resolutionNotes;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }

    public UUID resolvedBy() {
        return resolvedBy;
    }
}
