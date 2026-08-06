package com.petvacay.booking.application;

import com.petvacay.booking.domain.event.BookingEvents;
import com.petvacay.booking.domain.model.Incident;
import com.petvacay.booking.domain.model.IncidentStatus;
import com.petvacay.booking.domain.model.IncidentType;
import com.petvacay.booking.ports.in.IncidentUseCase;
import com.petvacay.booking.ports.out.IncidentRepositoryPort;
import com.petvacay.shared.domain.DomainEvent;
import com.petvacay.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Registro de incidencias.
 *
 * <p>Se alimenta de los mismos eventos de dominio que el resto del sistema, por
 * el mismo motivo que {@code NotificationApplicationService}: si una disputa se
 * abre, su evento existe, y con el la fila del registro — no hay forma de abrir
 * una incidencia por un camino y que no quede anotada.
 */
@Service
public class IncidentApplicationService implements IncidentUseCase {

    private final IncidentRepositoryPort incidents;
    private final Clock clock;

    public IncidentApplicationService(IncidentRepositoryPort incidents, Clock clock) {
        this.incidents = incidents;
        this.clock = clock;
    }

    /** Traduce los eventos recien levantados por un agregado en incidencias. */
    @Transactional
    public void recordAll(List<DomainEvent> events) {
        events.forEach(this::record);
    }

    private void record(DomainEvent event) {
        if (event instanceof BookingEvents.DisputaAbiertaEvent e) {
            openDispute(e);
        } else if (event instanceof BookingEvents.MascotaEnPeligroEvent e) {
            incidents.save(Incident.open(e.bookingId(), e.clientId(), e.sitterId(),
                    IncidentType.ALERTA_MEDICA, e.description(), e.sitterId(), clock.instant()));
        } else if (event instanceof BookingEvents.DisputaResueltaEvent e) {
            // La resolucion de la disputa cierra su incidencia: si hubiera que
            // cerrarla aparte, el panel acabaria mostrando como pendiente algo
            // que el propio administrador acaba de resolver.
            incidents.findOpenByBookingAndType(e.bookingId(), IncidentType.DISPUTA)
                    .ifPresent(incident -> {
                        incident.close(e.resolutionNotes(), e.resolvedBy(), clock.instant());
                        incidents.save(incident);
                    });
        }
    }

    /**
     * El evento de disputa no lleva las partes de la reserva, solo quien la
     * abrio: se deducen sabiendo que quien abre es siempre una de las dos.
     */
    private void openDispute(BookingEvents.DisputaAbiertaEvent event) {
        incidents.save(Incident.open(event.bookingId(), event.clientId(), event.sitterId(),
                IncidentType.DISPUTA, event.reason(), event.openedBy(), clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Incident> list(IncidentStatus status) {
        return incidents.findByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public com.petvacay.shared.domain.Page<Incident> search(IncidentStatus status, IncidentType type,
                                                            int page, int size) {
        return incidents.search(status, type, page, size);
    }

    @Override
    @Transactional
    public Incident close(UUID incidentId, UUID adminId, String notes) {
        Incident incident = incidents.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ERR_INCIDENT_NOT_FOUND", "No existe la incidencia " + incidentId));

        incident.close(notes, adminId, clock.instant());
        return incidents.save(incident);
    }
}
