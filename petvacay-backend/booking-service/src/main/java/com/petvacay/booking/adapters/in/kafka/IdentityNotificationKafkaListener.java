package com.petvacay.booking.adapters.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petvacay.booking.application.NotificationApplicationService;
import com.petvacay.identity.domain.event.IdentityEvents;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consume los topics de identidad que generan avisos en la campana.
 *
 * <p>Es la unica dependencia de booking-service hacia eventos de otro servicio, y
 * es deliberada: el buzon de notificaciones es de la plataforma entera, no solo
 * de las reservas. Solo lee — no toca reservas ni cuentas — asi que no crea
 * acoplamiento de escritura entre ambos contextos.
 */
@Component
@ConditionalOnProperty(name = "petvacay.messaging.provider", havingValue = "kafka")
public class IdentityNotificationKafkaListener {

    private static final String GROUP_ID = "petvacay-booking-notifications";

    private final NotificationApplicationService notifications;
    private final ObjectMapper objectMapper;

    public IdentityNotificationKafkaListener(NotificationApplicationService notifications,
                                             ObjectMapper objectMapper) {
        this.notifications = notifications;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = IdentityEvents.CuidadorPendienteVerificacionEvent.TOPIC, groupId = GROUP_ID)
    public void onCuidadorPendiente(String payload) throws Exception {
        notifications.recordAll(List.of(objectMapper.readValue(
                payload, IdentityEvents.CuidadorPendienteVerificacionEvent.class)));
    }

    @KafkaListener(topics = IdentityEvents.CuidadorVerificadoEvent.TOPIC, groupId = GROUP_ID)
    public void onCuidadorVerificado(String payload) throws Exception {
        notifications.recordAll(List.of(objectMapper.readValue(
                payload, IdentityEvents.CuidadorVerificadoEvent.class)));
    }

    @KafkaListener(topics = IdentityEvents.CuidadorRechazadoEvent.TOPIC, groupId = GROUP_ID)
    public void onCuidadorRechazado(String payload) throws Exception {
        notifications.recordAll(List.of(objectMapper.readValue(
                payload, IdentityEvents.CuidadorRechazadoEvent.class)));
    }
}
