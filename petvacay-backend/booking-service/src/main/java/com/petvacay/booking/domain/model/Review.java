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
