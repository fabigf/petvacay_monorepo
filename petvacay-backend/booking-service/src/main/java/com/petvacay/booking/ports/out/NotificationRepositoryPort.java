package com.petvacay.booking.ports.out;

import com.petvacay.booking.domain.model.Notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SPI del buzon de avisos en la aplicacion. */
public interface NotificationRepositoryPort {

    void saveAll(List<Notification> notifications);

    Notification save(Notification notification);

    Optional<Notification> findById(UUID notificationId);

    /** Avisos de los destinatarios indicados, mas recientes primero. */
    List<Notification> findForRecipients(List<UUID> recipientIds, int limit);

    /** Marca como leidos todos los del destinatario; devuelve cuantos cambiaron. */
    int markAllRead(List<UUID> recipientIds, java.time.Instant now);
}
