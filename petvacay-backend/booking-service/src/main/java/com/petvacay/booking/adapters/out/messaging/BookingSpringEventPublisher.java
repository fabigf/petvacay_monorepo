package com.petvacay.booking.adapters.out.messaging;

import com.petvacay.booking.ports.out.BookingEventPublisherPort;
import com.petvacay.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publicador de eventos de reserva sobre el bus interno de Spring.
 *
 * <p>Desde la Fase 4 no hay ningun consumidor en el mismo proceso (search-service
 * y payment-service son ahora servicios aparte): se conserva para poder levantar
 * booking-service en solitario sin Kafka mientras se itera sobre su REST API o su
 * dominio, asumiendo que los efectos derivados (proyeccion del buscador, apuntes
 * de escrow) no se produciran hasta que se ejecute con Kafka.
 */
@Component
@ConditionalOnProperty(name = "petvacay.messaging.provider", havingValue = "spring", matchIfMissing = true)
public class BookingSpringEventPublisher implements BookingEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(BookingSpringEventPublisher.class);

    private final ApplicationEventPublisher publisher;

    public BookingSpringEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(List<DomainEvent> events) {
        events.forEach(event -> {
            log.debug("Publicando evento de reserva {} ({})", event.eventName(), event.eventId());
            publisher.publishEvent(event);
        });
    }
}
