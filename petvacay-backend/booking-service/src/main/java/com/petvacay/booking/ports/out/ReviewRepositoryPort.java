package com.petvacay.booking.ports.out;

import com.petvacay.booking.domain.model.Review;
import com.petvacay.booking.domain.model.ReviewAuthorRole;

import java.util.List;
import java.util.UUID;

/** SPI de persistencia de resenas. */
public interface ReviewRepositoryPort {

    Review save(Review review);

    /** Una unica resena por reserva y rol de autor: no se puede valorar dos veces. */
    boolean existsByBookingIdAndAuthorRole(UUID bookingId, ReviewAuthorRole authorRole);

    List<Review> findByBookingId(UUID bookingId);

    List<Review> findBySubjectIdOrderByCreatedAtDesc(UUID subjectId);
}
