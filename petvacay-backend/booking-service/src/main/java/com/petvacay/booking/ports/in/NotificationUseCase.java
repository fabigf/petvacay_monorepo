package com.petvacay.booking.ports.in;

import com.petvacay.booking.domain.model.Notification;

import java.util.List;
import java.util.UUID;

/** Buzon de avisos de la aplicacion: leerlos y marcarlos como leidos. */
public interface NotificationUseCase {

    /**
     * Avisos del usuario, mas recientes primero. Si es administrador incluye
     * ademas los dirigidos al equipo (disputas, alertas medicas).
     */
    List<Notification> list(UUID userId, boolean isAdmin);

    Notification markRead(UUID notificationId, UUID userId, boolean isAdmin);

    int markAllRead(UUID userId, boolean isAdmin);
}
