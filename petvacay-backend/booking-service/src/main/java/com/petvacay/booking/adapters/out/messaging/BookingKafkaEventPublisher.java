package com.petvacay.booking.adapters.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petvacay.booking.ports.out.BookingEventPublisherPort;
import com.petvacay.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publicador de eventos de reserva sobre Apache Kafka (Fase 3, obligatorio entre
 * servicios desde la Fase 4). JSON plano, key = {@code aggregateId()} para
 * mantener el orden por reserva en la particion.
 */
@Component
@ConditionalOnProperty(name = "petvacay.messaging.provider", havingValue = "kafka")
public class BookingKafkaEventPublisher implements BookingEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(BookingKafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public BookingKafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(List<DomainEvent> events) {
        events.forEach(this::publishOne);
    }

    private void publishOne(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(event.eventName(), event.aggregateId().toString(), payload);
            log.debug("Publicado {} ({}) en el topic {}", event.eventName(), event.eventId(), event.eventName());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "No se pudo serializar el evento " + event.eventName() + " a JSON", e);
        }
    }
}
