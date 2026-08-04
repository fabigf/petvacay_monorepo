package com.petvacay.payment.adapters.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petvacay.booking.domain.event.BookingEvents;
import com.petvacay.payment.application.SettlementHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada que consume los topics de reserva relevantes para el escrow
 * y delega en {@link SettlementHandler}. Ver {@code SitterProjectionKafkaListener}
 * para el razonamiento completo de este patron de adaptador.
 */
@Component
@ConditionalOnProperty(name = "petvacay.messaging.provider", havingValue = "kafka")
public class SettlementKafkaListener {

    private static final String GROUP_ID = "petvacay-payment-settlement";

    private final SettlementHandler handler;
    private final ObjectMapper objectMapper;

    public SettlementKafkaListener(SettlementHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = BookingEvents.PagoConfirmadoEvent.TOPIC, groupId = GROUP_ID)
    public void onPagoConfirmado(String payload) throws Exception {
        handler.onPagoConfirmado(objectMapper.readValue(payload, BookingEvents.PagoConfirmadoEvent.class));
    }

    @KafkaListener(topics = BookingEvents.ReservaFinalizadaEvent.TOPIC, groupId = GROUP_ID)
    public void onReservaFinalizada(String payload) throws Exception {
        handler.onReservaFinalizada(objectMapper.readValue(payload, BookingEvents.ReservaFinalizadaEvent.class));
    }

    @KafkaListener(topics = BookingEvents.ReservaCanceladaEvent.TOPIC, groupId = GROUP_ID)
    public void onReservaCancelada(String payload) throws Exception {
        handler.onReservaCancelada(objectMapper.readValue(payload, BookingEvents.ReservaCanceladaEvent.class));
    }
}
