package com.petvacay.booking.adapters.out.persistence;

import com.petvacay.booking.domain.model.Notification;
import com.petvacay.booking.ports.out.NotificationRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class NotificationPersistenceAdapter implements NotificationRepositoryPort {

    private final NotificationJpaRepository repository;

    public NotificationPersistenceAdapter(NotificationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveAll(List<Notification> notifications) {
        repository.saveAll(notifications.stream().map(NotificationEntity::fromDomain).toList());
    }

    @Override
    public Notification save(Notification notification) {
        return repository.save(NotificationEntity.fromDomain(notification)).toDomain();
    }

    @Override
    public Optional<Notification> findById(UUID notificationId) {
        return repository.findById(notificationId).map(NotificationEntity::toDomain);
    }

    @Override
    public List<Notification> findForRecipients(List<UUID> recipientIds, int limit) {
        return repository
                .findByRecipientIdInOrderByCreatedAtDesc(recipientIds, PageRequest.of(0, limit)).stream()
                .map(NotificationEntity::toDomain).toList();
    }

    @Override
    public int markAllRead(List<UUID> recipientIds, Instant now) {
        return repository.markAllRead(recipientIds, now);
    }
}
