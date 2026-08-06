package com.petvacay.booking.ports.in;

import com.petvacay.booking.domain.model.Incident;
import com.petvacay.booking.domain.model.IncidentStatus;
import com.petvacay.booking.domain.model.IncidentType;
import com.petvacay.shared.domain.Page;

import java.util.List;
import java.util.UUID;

/** Registro de incidencias para el back-office (negocio.md 2.3). */
public interface IncidentUseCase {

    /** Incidencias del estado indicado; con {@code null}, todas. */
    List<Incident> list(IncidentStatus status);

    /** Historico paginado, filtrable por estado y por tipo. */
    Page<Incident> search(IncidentStatus status, IncidentType type, int page, int size);

    /**
     * Marca una incidencia como atendida. Las disputas se cierran solas al
     * dictarse su resolucion; esto es para las alertas medicas, que no tienen
     * una transicion de reserva que las cierre por su cuenta.
     */
    Incident close(UUID incidentId, UUID adminId, String notes);
}
