package com.petvacay.booking.application;

import com.petvacay.booking.domain.model.Notification;
import com.petvacay.booking.ports.in.NotificationUseCase;
import com.petvacay.booking.ports.out.NotificationRepositoryPort;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import com.petvacay.shared.domain.DomainEvent;
import com.petvacay.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Buzon de avisos de la aplicacion.
 *
 * <p>Escribe desde {@link #recordAll(List)}, que invoca {@code BookingApplicationService}
 * con los mismos eventos que va a publicar, dentro de su misma transaccion: si la
 * reserva no llega a guardarse, tampoco queda el aviso de que ocurrio algo que no
 * ocurrio.
 */
@Service
public class NotificationApplicationService implements NotificationUseCase {

    /** Tope del desplegable de la campana; nadie baja mas alla en un menu. */
    private static final int FEED_LIMIT = 30;

    private final NotificationRepositoryPort notifications;
    private final Clock clock;

    public NotificationApplicationService(NotificationRepositoryPort notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Convierte los eventos recien levantados por un agregado en avisos. */
    @Transactional
    public void recordAll(List<DomainEvent> events) {
        List<Notification> created = events.stream()
                .flatMap(event -> NotificationFactory.from(event, clock.instant()).stream())
                .toList();

        if (!created.isEmpty()) {
            notifications.saveAll(created);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> list(UUID userId, boolean isAdmin) {
        return notifications.findForRecipients(recipients(userId, isAdmin), FEED_LIMIT);
    }

    @Override
    @Transactional
    public Notification markRead(UUID notificationId, UUID userId, boolean isAdmin) {
        Notification notification = notifications.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ERR_NOTIFICATION_NOT_FOUND", "No existe el aviso " + notificationId));

        if (!recipients(userId, isAdmin).contains(notification.recipientId())) {
            throw new BusinessRuleViolationException("ERR_NOT_NOTIFICATION_RECIPIENT", "Acceso denegado",
                    403, "Este aviso no es tuyo.");
        }

        notification.markRead(clock.instant());
        return notifications.save(notification);
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId, boolean isAdmin) {
        return notifications.markAllRead(recipients(userId, isAdmin), clock.instant());
    }

    /**
     * Buzones que puede leer este usuario. El del equipo de administracion es una
     * audiencia compartida, no una cuenta: cualquier administrador ve — y puede
     * dar por leidos — los mismos avisos.
     */
    private List<UUID> recipients(UUID userId, boolean isAdmin) {
        return isAdmin ? List.of(userId, Notification.ADMIN_AUDIENCE) : List.of(userId);
    }
}
