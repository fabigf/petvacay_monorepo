package com.petvacay.booking.adapters.out.persistence;

import com.petvacay.booking.domain.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingJpaRepository extends JpaRepository<BookingEntity, UUID> {

    List<BookingEntity> findByClientIdOrderByStartDateDesc(UUID clientId);

    List<BookingEntity> findBySitterIdOrderByStartDateDesc(UUID sitterId);

    /** Reservas del cuidador que solapan con [start, end) en los estados indicados. */
    @Query("""
            select b from BookingEntity b
            where b.sitterId = :sitterId
              and b.status in :statuses
              and b.startDate < :end
              and :start < b.endDate
            """)
    List<BookingEntity> findOverlapping(@Param("sitterId") UUID sitterId,
                                        @Param("start") LocalDate start,
                                        @Param("end") LocalDate end,
                                        @Param("statuses") Collection<BookingStatus> statuses);

    List<BookingEntity> findByStatusAndRequestedAtBefore(BookingStatus status, Instant threshold);
}
