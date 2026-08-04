package com.petvacay.booking.adapters.out.persistence;

import com.petvacay.booking.domain.model.Review;
import com.petvacay.booking.domain.model.ReviewAuthorRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
public class ReviewEntity {

    @Id
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false)
    private ReviewAuthorRole authorRole;

    @Column(nullable = false)
    private int rating;

    @Column(length = 2000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReviewEntity() {
    }

    public static ReviewEntity fromDomain(Review review) {
        ReviewEntity entity = new ReviewEntity();
        entity.id = review.id();
        entity.bookingId = review.bookingId();
        entity.authorId = review.authorId();
        entity.subjectId = review.subjectId();
        entity.authorRole = review.authorRole();
        entity.rating = review.rating();
        entity.comment = review.comment();
        entity.createdAt = review.createdAt();
        return entity;
    }

    public Review toDomain() {
        return Review.rehydrate(id, bookingId, authorId, subjectId, authorRole, rating, comment, createdAt);
    }
}
