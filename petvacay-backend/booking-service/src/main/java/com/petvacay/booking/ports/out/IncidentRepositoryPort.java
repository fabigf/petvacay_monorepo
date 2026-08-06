package com.petvacay.booking.ports.out;

import com.petvacay.booking.domain.model.Incident;
import com.petvacay.booking.domain.model.IncidentStatus;
import com.petvacay.booking.domain.model.IncidentType;
import com.petvacay.shared.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SPI del registro de incidencias. */
public interface IncidentRepositoryPort {

    Incident save(Incident incident);

    Optional<Incident> findById(UUID incidentId);

    /** Todas las incidencias, o solo las de un estado; mas recientes primero. */
    List<Incident> findByStatus(IncidentStatus status);

    /**
     * Historico paginado y filtrable por estado y tipo. Cada filtro se anula con
     * {@code null}: el panel combina ambos y no tiene sentido una consulta
     * distinta por combinacion.
     */
    Page<Incident> search(IncidentStatus status, IncidentType type, int page, int size);

    /**
     * Incidencia abierta de un tipo concreto sobre una reserva. La usa el cierre
     * automatico: al resolver una disputa hay que encontrar la que se abrio.
     */
    Optional<Incident> findOpenByBookingAndType(UUID bookingId, IncidentType type);
}
