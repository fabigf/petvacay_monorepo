package com.petvacay.identity.adapters.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petvacay.identity.ports.out.IdentityPorts.IdentityEventPublisherPort;
import com.petvacay.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publicador de eventos de identidad sobre Apache Kafka (Fase 3, obligatorio
 * entre servicios desde la Fase 4). JSON plano, key = {@code aggregateId()};
 * ver {@code KafkaDomainEventPublisher} del monolito original para el
 * razonamiento completo, que no cambia.
 */
@Component
@ConditionalOnProperty(name = "petvacay.messaging.provider", havingValue = "kafka")
public class IdentityKafkaEventPublisher implements IdentityEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(IdentityKafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public IdentityKafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
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
