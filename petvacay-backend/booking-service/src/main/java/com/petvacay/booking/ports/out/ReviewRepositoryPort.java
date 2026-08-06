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

    /**
     * Reputacion publica acumulada de cada cuidador valorado: la suma de
     * puntuaciones y el numero de resenas que cuentan de cara al buscador.
     *
     * <p>Es la fuente de verdad con la que se reconstruye la proyeccion cuando
     * esta se desincroniza. Se calcula en la base de datos y no cargando todas
     * las resenas en memoria: el numero de filas crece sin techo.
     */
    List<SitterReputation> summarizePublicReputation();

    /** Valores absolutos, no incrementos: por eso reproyectar es idempotente. */
    record SitterReputation(UUID sitterId, long ratingSum, int reviewCount) {
    }
}
