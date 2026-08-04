package com.petvacay.booking.adapters.out.persistence;

import com.petvacay.booking.domain.model.ReviewAuthorRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewJpaRepository extends JpaRepository<ReviewEntity, UUID> {

    boolean existsByBookingIdAndAuthorRole(UUID bookingId, ReviewAuthorRole authorRole);

    List<ReviewEntity> findByBookingId(UUID bookingId);

    List<ReviewEntity> findBySubjectIdOrderByCreatedAtDesc(UUID subjectId);
}
