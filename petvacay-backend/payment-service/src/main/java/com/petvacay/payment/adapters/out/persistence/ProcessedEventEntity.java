package com.petvacay.payment.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    /** El eventId del evento de dominio: la clave primaria hace de candado. */
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {
    }

    public ProcessedEventEntity(UUID eventId, String eventName, Instant processedAt) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.processedAt = processedAt;
    }
}
