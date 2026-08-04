package com.petvacay.shared.domain;

import java.time.Instant;
import java.util.UUID;


/**
 * Contrato de los eventos de dominio (ver negocio.md seccion 5).
 * En Fase 1/2 se publican con el bus interno de Spring; en Fase 3 el mismo
 * contrato viaja por Apache Kafka sin tocar el nucleo de dominio.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    /** Nombre logico del evento; es el topic de Kafka en Fase 3. */
    String eventName();

    /**
     * Identificador del agregado que origina el evento (la reserva o el cuidador).
     * Se usa como key del mensaje de Kafka para que todos los eventos de un mismo
     * agregado caigan en la misma particion y se procesen en orden.
     */
    UUID aggregateId();
}
