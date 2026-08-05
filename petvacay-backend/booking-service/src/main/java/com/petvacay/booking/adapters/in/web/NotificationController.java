package com.petvacay.booking.adapters.in.web;

import com.petvacay.booking.domain.model.Notification;
import com.petvacay.booking.domain.model.NotificationType;
import com.petvacay.booking.ports.in.NotificationUseCase;
import com.petvacay.shared.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Campana de avisos de la cabecera.
 *
 * <p>El rol se lee del token y no se acepta como parametro: es lo que decide si
 * ademas del buzon propio se entrega el del equipo de administracion.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationUseCase notifications;

    public NotificationController(NotificationUseCase notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<NotificationResponse> list(@AuthenticatedUser UUID userId, Authentication authentication) {
        return notifications.list(userId, isAdmin(authentication)).stream()
                .map(NotificationResponse::from).toList();
    }

    @PostMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable UUID notificationId,
                                         @AuthenticatedUser UUID userId,
                                         Authentication authentication) {
        return NotificationResponse.from(
                notifications.markRead(notificationId, userId, isAdmin(authentication)));
    }

    @PostMapping("/read-all")
    public MarkAllReadResponse markAllRead(@AuthenticatedUser UUID userId, Authentication authentication) {
        return new MarkAllReadResponse(notifications.markAllRead(userId, isAdmin(authentication)));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    public record NotificationResponse(
            UUID notificationId,
            NotificationType type,
            String title,
            String body,
            UUID bookingId,
            Instant createdAt,
            boolean unread) {

        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(notification.id(), notification.type(), notification.title(),
                    notification.body(), notification.bookingId(), notification.createdAt(),
                    notification.isUnread());
        }
    }

    public record MarkAllReadResponse(int updated) {
    }
}
