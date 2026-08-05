package com.petvacay.booking.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aviso en la propia aplicacion: lo que muestra la campana de la cabecera.
 *
 * <p>Es deliberadamente mas simple que el Notification Service que describe
 * {@code estructura.md} (correo, push, SMS): aquel es un servicio aparte con sus
 * propios proveedores externos y sus reintentos; esto es un buzon dentro de la
 * app, alimentado por los mismos eventos de dominio. Cuando exista el servicio
 * real, este buzon sera uno mas de sus canales de salida, no un competidor.
 *
 * <p>El contenido se guarda ya redactado en vez de reconstruirse al leer: una
 * notificacion cuenta lo que paso <em>cuando</em> paso, y una reserva que despues
 * se cancela no debe reescribir el aviso de que fue aceptada.
 */
public final class Notification {

    /**
     * Destinatario ficticio de los avisos dirigidos "al equipo de administracion".
     *
     * <p>Booking-service no conoce las cuentas de administrador — viven en
     * identity-service — asi que no puede guardar una fila por administrador.
     * Guarda una sola dirigida a esta audiencia, y el listado se la entrega a
     * quien presente un token con {@code ROLE_ADMIN}.
     */
    public static final UUID ADMIN_AUDIENCE = UUID.fromString("00000000-0000-0000-0000-0000000000ad");

    private final UUID id;
    private final UUID recipientId;
    private final NotificationType type;
    private final String title;
    private final String body;
    private final UUID bookingId;
    private final Instant createdAt;
    private Instant readAt;

    private Notification(UUID id, UUID recipientId, NotificationType type, String title, String body,
                         UUID bookingId, Instant createdAt, Instant readAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.recipientId = Objects.requireNonNull(recipientId, "recipientId");
        this.type = Objects.requireNonNull(type, "type");
        this.title = Objects.requireNonNull(title, "title");
        this.body = body;
        this.bookingId = bookingId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.readAt = readAt;
    }

    public static Notification create(UUID recipientId, NotificationType type, String title, String body,
                                      UUID bookingId, Instant now) {
        return new Notification(UUID.randomUUID(), recipientId, type, title, body, bookingId, now, null);
    }

    public static Notification rehydrate(UUID id, UUID recipientId, NotificationType type, String title,
                                         String body, UUID bookingId, Instant createdAt, Instant readAt) {
        return new Notification(id, recipientId, type, title, body, bookingId, createdAt, readAt);
    }

    /** Marcar como leida es idempotente: la primera lectura fija la fecha. */
    public void markRead(Instant now) {
        if (readAt == null) {
            this.readAt = now;
        }
    }

    public boolean isUnread() {
        return readAt == null;
    }

    public UUID id() {
        return id;
    }

    public UUID recipientId() {
        return recipientId;
    }

    public NotificationType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant readAt() {
        return readAt;
    }
}
