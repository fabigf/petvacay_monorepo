package com.petvacay.booking.adapters.out.persistence;

import com.petvacay.booking.domain.model.Notification;
import com.petvacay.booking.domain.model.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String body;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected NotificationEntity() {
    }

    public static NotificationEntity fromDomain(Notification notification) {
        NotificationEntity entity = new NotificationEntity();
        entity.id = notification.id();
        entity.recipientId = notification.recipientId();
        entity.type = notification.type();
        entity.title = notification.title();
        entity.body = notification.body();
        entity.bookingId = notification.bookingId();
        entity.createdAt = notification.createdAt();
        entity.readAt = notification.readAt();
        return entity;
    }

    public Notification toDomain() {
        return Notification.rehydrate(id, recipientId, type, title, body, bookingId, createdAt, readAt);
    }
}
