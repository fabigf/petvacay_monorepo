package com.petvacay.booking.adapters.out.persistence;

import com.petvacay.booking.domain.model.Review;
import com.petvacay.booking.domain.model.ReviewAuthorRole;
import com.petvacay.booking.ports.out.ReviewRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ReviewPersistenceAdapter implements ReviewRepositoryPort {

    private final ReviewJpaRepository repository;

    public ReviewPersistenceAdapter(ReviewJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Review save(Review review) {
        return repository.save(ReviewEntity.fromDomain(review)).toDomain();
    }

    @Override
    public boolean existsByBookingIdAndAuthorRole(UUID bookingId, ReviewAuthorRole authorRole) {
        return repository.existsByBookingIdAndAuthorRole(bookingId, authorRole);
    }

    @Override
    public List<Review> findByBookingId(UUID bookingId) {
        return repository.findByBookingId(bookingId).stream().map(ReviewEntity::toDomain).toList();
    }

    @Override
    public List<Review> findBySubjectIdOrderByCreatedAtDesc(UUID subjectId) {
        return repository.findBySubjectIdOrderByCreatedAtDesc(subjectId).stream()
                .map(ReviewEntity::toDomain).toList();
    }
}
