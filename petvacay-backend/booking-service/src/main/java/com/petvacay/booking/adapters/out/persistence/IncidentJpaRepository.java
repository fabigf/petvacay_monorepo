package com.petvacay.booking.adapters.out.persistence;

import com.petvacay.booking.domain.model.IncidentStatus;
import com.petvacay.booking.domain.model.IncidentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentJpaRepository extends JpaRepository<IncidentEntity, UUID> {

    List<IncidentEntity> findAllByOrderByOpenedAtDesc();

    List<IncidentEntity> findByStatusOrderByOpenedAtDesc(IncidentStatus status);

    Optional<IncidentEntity> findFirstByBookingIdAndTypeAndStatus(UUID bookingId, IncidentType type,
                                                                  IncidentStatus status);

    /** Historico del panel: estado y tipo opcionales, mas recientes primero. */
    @Query("""
            select i from IncidentEntity i
            where (:status is null or i.status = :status)
              and (:type is null or i.type = :type)
            order by i.openedAt desc
            """)
    Page<IncidentEntity> search(@Param("status") IncidentStatus status,
                                @Param("type") IncidentType type,
                                Pageable pageable);
}
