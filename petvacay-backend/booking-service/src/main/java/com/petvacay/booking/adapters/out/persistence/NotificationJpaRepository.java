package com.petvacay.booking.adapters.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByRecipientIdInOrderByCreatedAtDesc(Collection<UUID> recipientIds,
                                                                    Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            update NotificationEntity n set n.readAt = :now
            where n.recipientId in :recipientIds and n.readAt is null
            """)
    int markAllRead(@Param("recipientIds") Collection<UUID> recipientIds, @Param("now") Instant now);
}
