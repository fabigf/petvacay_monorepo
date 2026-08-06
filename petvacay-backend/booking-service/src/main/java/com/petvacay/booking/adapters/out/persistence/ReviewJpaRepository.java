package com.petvacay.booking.adapters.out.persistence;

import com.petvacay.booking.domain.model.ReviewAuthorRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReviewJpaRepository extends JpaRepository<ReviewEntity, UUID> {

    boolean existsByBookingIdAndAuthorRole(UUID bookingId, ReviewAuthorRole authorRole);

    List<ReviewEntity> findByBookingId(UUID bookingId);

    List<ReviewEntity> findBySubjectIdOrderByCreatedAtDesc(UUID subjectId);

    /**
     * Reputacion acumulada por cada persona valorada, excluyendo el rol indicado.
     *
     * <p>Se le pasa {@code SITTER} para dejar fuera las resenas que un cuidador
     * escribio sobre un cliente: van dirigidas a otra persona y no forman parte
     * de la reputacion que el buscador muestra.
     */
    @Query("""
            select r.subjectId as sitterId, sum(r.rating) as ratingSum, count(r) as reviewCount
            from ReviewEntity r
            where r.authorRole <> :excludedRole
            group by r.subjectId
            """)
    List<SitterReputationProjection> summarizeReputationExcluding(
            @Param("excludedRole") ReviewAuthorRole excludedRole);

    /**
     * Proyeccion por interfaz en vez de construir el record en el propio JPQL:
     * el constructor exige que los tipos casen exactamente y las funciones de
     * agregacion devuelven {@code Long}, asi que el mapeo se hace en el
     * adaptador, donde el cast es explicito y visible.
     */
    interface SitterReputationProjection {

        UUID getSitterId();

        long getRatingSum();

        long getReviewCount();
    }
}
