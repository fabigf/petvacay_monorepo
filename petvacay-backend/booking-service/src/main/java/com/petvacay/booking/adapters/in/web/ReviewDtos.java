package com.petvacay.booking.adapters.in.web;

import com.petvacay.booking.domain.model.Review;
import com.petvacay.booking.domain.model.ReviewAuthorRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Contratos JSON del modulo de resenas. */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record CreateReviewRequest(
            @NotNull(message = "La puntuación es obligatoria")
            @Min(value = 1, message = "La puntuación mínima es 1") @Max(value = 5, message = "La puntuación máxima es 5")
            Integer rating,
            @Size(max = 2000) String comment) {
    }

    public record ReviewResponse(
            UUID reviewId,
            UUID bookingId,
            UUID authorId,
            UUID subjectId,
            ReviewAuthorRole authorRole,
            int rating,
            String comment,
            Instant createdAt) {

        public static ReviewResponse from(Review review) {
            return new ReviewResponse(review.id(), review.bookingId(), review.authorId(), review.subjectId(),
                    review.authorRole(), review.rating(), review.comment(), review.createdAt());
        }
    }
}
