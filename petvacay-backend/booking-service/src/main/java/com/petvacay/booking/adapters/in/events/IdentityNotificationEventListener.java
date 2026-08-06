package com.petvacay.booking.adapters.in.events;

import com.petvacay.booking.application.NotificationApplicationService;
import com.petvacay.identity.domain.event.IdentityEvents;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convierte en avisos de la campana los eventos que nacen en identity-service.
 *
 * <p>El centro de notificaciones vive en booking-service porque casi todo lo que
 * hay que avisar nace de una reserva, pero el buzon es de la plataforma entera:
 * si "tienes un KYC pendiente" no cupiera aqui, el administrador tendria dos
 * bandejas distintas que vigilar y ninguna le contaria toda su lista de tareas.
 *
 * <p>Su homologo para Kafka es {@code IdentityNotificationKafkaListener}; ambos
 * son mutuamente excluyentes, igual que el resto de adaptadores de mensajeria.
 */
@Component
@ConditionalOnProperty(name = "petvacay.messaging.provider", havingValue = "spring", matchIfMissing = true)
public class IdentityNotificationEventListener {

    private final NotificationApplicationService notifications;

    public IdentityNotificationEventListener(NotificationApplicationService notifications) {
        this.notifications = notifications;
    }

    @EventListener
    public void on(IdentityEvents.CuidadorPendienteVerificacionEvent event) {
        notifications.recordAll(List.of(event));
    }

    @EventListener
    public void on(IdentityEvents.CuidadorVerificadoEvent event) {
        notifications.recordAll(List.of(event));
    }

    @EventListener
    public void on(IdentityEvents.CuidadorRechazadoEvent event) {
        notifications.recordAll(List.of(event));
    }
}
