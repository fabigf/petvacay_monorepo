package com.petvacay.identity.adapters.out.messaging;

import com.petvacay.identity.ports.out.IdentityPorts.IdentityEventPublisherPort;
import com.petvacay.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publicador de eventos de identidad sobre el bus interno de Spring.
 *
 * <p>Desde la Fase 4, identity-service es un proceso independiente: ya no hay
 * ningun consumidor en la misma JVM que escuche estos eventos (el proyector del
 * buscador vive ahora en search-service). Este publicador se conserva sobre todo
 * para poder levantar identity-service en solitario, sin Kafka, cuando se esta
 * iterando solo sobre su REST API o su logica de dominio: los eventos se emiten
 * igualmente pero no llegan a ningun sitio, lo cual es el comportamiento esperado
 * al ejecutar un unico microservicio de forma aislada.
 */
@Component
@ConditionalOnProperty(name = "petvacay.messaging.provider", havingValue = "spring", matchIfMissing = true)
public class IdentitySpringEventPublisher implements IdentityEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(IdentitySpringEventPublisher.class);

    private final ApplicationEventPublisher publisher;

    public IdentitySpringEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(List<DomainEvent> events) {
        events.forEach(event -> {
            log.debug("Publicando evento de identidad {} ({})", event.eventName(), event.eventId());
            publisher.publishEvent(event);
        });
    }
}
