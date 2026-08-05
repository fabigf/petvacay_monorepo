package com.petvacay.search.adapters.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petvacay.booking.domain.event.BookingEvents;
import com.petvacay.identity.domain.event.IdentityEvents;
import com.petvacay.search.application.SitterProjectionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada que consume los topics de Kafka
 * ({@code petvacay.messaging.provider=kafka}, Fase 3) y delega en
 * {@link SitterProjectionHandler}.
 *
 * <p>Cada evento viaja como JSON plano; este listener lo deserializa al tipo
 * concreto de {@code BookingEvents}/{@code IdentityEvents} antes de invocar el
 * handler, que es identico al que usa el bus interno de Spring
 * ({@code SitterProjectionEventListener}). El grupo de consumidor
 * {@code petvacay-search-projector} es propio de este modulo: en Fase 4, cuando el
 * buscador sea un microservicio independiente, seguira consumiendo los mismos
 * topics sin cambios.
 */
@Component
@ConditionalOnProperty(name = "petvacay.messaging.provider", havingValue = "kafka")
public class SitterProjectionKafkaListener {

    private static final String GROUP_ID = "petvacay-search-projector";

    private final SitterProjectionHandler handler;
    private final ObjectMapper objectMapper;

    public SitterProjectionKafkaListener(SitterProjectionHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = IdentityEvents.CuidadorIndexadoEvent.TOPIC, groupId = GROUP_ID)
    public void onCuidadorIndexado(String payload) throws Exception {
        handler.onCuidadorIndexado(objectMapper.readValue(payload, IdentityEvents.CuidadorIndexadoEvent.class));
    }

    @KafkaListener(topics = IdentityEvents.CuidadorRechazadoEvent.TOPIC, groupId = GROUP_ID)
    public void onCuidadorRechazado(String payload) throws Exception {
        handler.onCuidadorRechazado(objectMapper.readValue(payload, IdentityEvents.CuidadorRechazadoEvent.class));
    }

    @KafkaListener(topics = BookingEvents.PagoConfirmadoEvent.TOPIC, groupId = GROUP_ID)
    public void onPagoConfirmado(String payload) throws Exception {
        handler.onPagoConfirmado(objectMapper.readValue(payload, BookingEvents.PagoConfirmadoEvent.class));
    }

    @KafkaListener(topics = BookingEvents.ReservaCanceladaEvent.TOPIC, groupId = GROUP_ID)
    public void onReservaCancelada(String payload) throws Exception {
        handler.onReservaCancelada(objectMapper.readValue(payload, BookingEvents.ReservaCanceladaEvent.class));
    }

    @KafkaListener(topics = BookingEvents.ReservaFinalizadaEvent.TOPIC, groupId = GROUP_ID)
    public void onReservaFinalizada(String payload) throws Exception {
        handler.onReservaFinalizada(objectMapper.readValue(payload, BookingEvents.ReservaFinalizadaEvent.class));
    }

    @KafkaListener(topics = BookingEvents.DisputaResueltaEvent.TOPIC, groupId = GROUP_ID)
    public void onDisputaResuelta(String payload) throws Exception {
        handler.onDisputaResuelta(objectMapper.readValue(payload, BookingEvents.DisputaResueltaEvent.class));
    }

    @KafkaListener(topics = BookingEvents.ResenaCreadaEvent.TOPIC, groupId = GROUP_ID)
    public void onResenaCreada(String payload) throws Exception {
        handler.onResenaCreada(objectMapper.readValue(payload, BookingEvents.ResenaCreadaEvent.class));
    }
}
