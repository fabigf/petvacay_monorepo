package com.petvacay.booking.ports.out;

import com.petvacay.shared.domain.DomainEvent;

import java.util.List;

/**
 * SPI de publicacion de eventos de dominio.
 * Fase 1-2: bus interno de Spring. Fase 3: productor de Apache Kafka.
 */
public interface BookingEventPublisherPort {

    void publish(List<DomainEvent> events);
}
