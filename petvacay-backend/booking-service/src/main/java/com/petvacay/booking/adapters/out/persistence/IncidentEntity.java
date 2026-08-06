package com.petvacay.booking.adapters.out.persistence;

import com.petvacay.booking.domain.model.Incident;
import com.petvacay.booking.domain.model.IncidentStatus;
import com.petvacay.booking.domain.model.IncidentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class IncidentEntity {

    @Id
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "sitter_id", nullable = false)
    private UUID sitterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncidentType type;

    @Column(length = 2000)
    private String description;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opened_by")
    private UUID openedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentStatus status;

    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    protected IncidentEntity() {
    }

    public static IncidentEntity fromDomain(Incident incident) {
        IncidentEntity entity = new IncidentEntity();
        entity.id = incident.id();
        entity.bookingId = incident.bookingId();
        entity.clientId = incident.clientId();
        entity.sitterId = incident.sitterId();
        entity.type = incident.type();
        entity.description = incident.description();
        entity.openedAt = incident.openedAt();
        entity.openedBy = incident.openedBy();
        entity.status = incident.status();
        entity.resolutionNotes = incident.resolutionNotes();
        entity.resolvedAt = incident.resolvedAt();
        entity.resolvedBy = incident.resolvedBy();
        return entity;
    }

    public Incident toDomain() {
        return Incident.rehydrate(id, bookingId, clientId, sitterId, type, description, openedAt,
                openedBy, status, resolutionNotes, resolvedAt, resolvedBy);
    }
}
