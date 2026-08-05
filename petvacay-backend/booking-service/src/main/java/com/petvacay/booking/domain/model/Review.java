package com.petvacay.booking.domain.model;

import com.petvacay.shared.domain.BusinessRuleViolationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Resena de una reserva ya finalizada (negocio.md: "reputacion auditable...
 * resenas cruzadas inmutables"). Inmutable a proposito: no existe metodo para
 * editarla ni borrarla, solo crearla — una resena que se pudiera retocar
 * despues dejaria de ser una garantia de nada.
 */
public final class Review {

    private final UUID id;
    private final UUID bookingId;
    private final UUID authorId;
    private final UUID subjectId;
    private final ReviewAuthorRole authorRole;
    private final int rating;
    private final String comment;
    private final Instant createdAt;

    private Review(UUID id, UUID bookingId, UUID authorId, UUID subjectId, ReviewAuthorRole authorRole,
                   int rating, String comment, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.authorId = Objects.requireNonNull(authorId, "authorId");
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.authorRole = Objects.requireNonNull(authorRole, "authorRole");
        this.rating = rating;
        this.comment = comment;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Review create(UUID bookingId, UUID authorId, UUID subjectId, ReviewAuthorRole authorRole,
                                int rating, String comment, Instant now) {
        if (rating < 1 || rating > 5) {
            throw BusinessRuleViolationException.unprocessable(
                    "ERR_REVIEW_INVALID_RATING", "Puntuación inválida",
                    "La puntuación debe ser un número entero entre 1 y 5.");
        }
        return new Review(UUID.randomUUID(), bookingId, authorId, subjectId, authorRole, rating, comment, now);
    }

    /**
     * Identificador del autor de las resenas que escribe la propia plataforma.
     * Es una constante, no una cuenta real: permite guardar el {@code author_id}
     * obligatorio sin inventarse un usuario ni dejar la columna nula, y hace que
     * estas resenas se reconozcan de un vistazo en la base de datos.
     */
    public static final UUID SYSTEM_AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** Puntuacion de la resena automatica por cancelacion del cuidador. */
    private static final int CANCELLATION_PENALTY_RATING = 1;

    /**
     * Resena negativa automatica por cancelar una reserva ya confirmada
     * (negocio.md 4.3: "ademas de una resena negativa del sistema"). El texto es
     * fijo y describe el hecho objetivo, sin juzgar el motivo: la plataforma
     * registra que la cancelacion ocurrio, no por que.
     */
    public static Review systemCancellationPenalty(UUID bookingId, UUID sitterId, Instant now) {
        return new Review(UUID.randomUUID(), bookingId, SYSTEM_AUTHOR_ID, sitterId,
                ReviewAuthorRole.SYSTEM, CANCELLATION_PENALTY_RATING,
                "El cuidador canceló esta reserva una vez confirmada.", now);
    }

    /** Rehidratacion desde el adaptador de persistencia. */
    public static Review rehydrate(UUID id, UUID bookingId, UUID authorId, UUID subjectId,
                                   ReviewAuthorRole authorRole, int rating, String comment, Instant createdAt) {
        return new Review(id, bookingId, authorId, subjectId, authorRole, rating, comment, createdAt);
    }

    public UUID id() {
        return id;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public UUID authorId() {
        return authorId;
    }

    public UUID subjectId() {
        return subjectId;
    }

    public ReviewAuthorRole authorRole() {
        return authorRole;
    }

    public int rating() {
        return rating;
    }

    public String comment() {
        return comment;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
