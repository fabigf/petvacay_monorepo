package com.petvacay.booking.adapters.in.web;

import com.petvacay.booking.domain.model.Incident;
import com.petvacay.booking.domain.model.IncidentStatus;
import com.petvacay.booking.domain.model.IncidentType;
import com.petvacay.booking.ports.in.IncidentUseCase;
import com.petvacay.shared.domain.Page;
import com.petvacay.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registro de incidencias del back-office: que ha pasado en cada reserva, a
 * quien afecta y que queda por atender (negocio.md 2.3).
 *
 * <p>Protegido por {@code hasRole("ADMIN")} en {@code SecurityConfig}, igual que
 * el resto de {@code /api/v1/admin/**}.
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/incidents")
public class AdminIncidentController {

    /** Mismo tope que los listados de identity-service, y por el mismo motivo. */
    private static final int MAX_PAGE_SIZE = 100;
    private static final String PAGE_MESSAGE = "La página no puede ser negativa";
    private static final String SIZE_MESSAGE = "El tamaño de página debe estar entre 1 y 100";

    private final IncidentUseCase incidents;

    public AdminIncidentController(IncidentUseCase incidents) {
        this.incidents = incidents;
    }

    /**
     * Historico paginado. Sin filtros devuelve todo; {@code status} y {@code type}
     * lo acotan y se combinan entre si.
     */
    @GetMapping
    public PageResponse<IncidentResponse> list(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentType type,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = PAGE_MESSAGE) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = SIZE_MESSAGE)
            @Max(value = MAX_PAGE_SIZE, message = SIZE_MESSAGE) int size) {

        Page<Incident> found = incidents.search(status, type, page, size);
        return new PageResponse<>(found.content().stream().map(IncidentResponse::from).toList(),
                found.page(), found.size(), found.totalElements(), found.totalPages());
    }

    /** Envoltorio de paginacion, con el mismo contrato que el de identity-service. */
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
                                  int totalPages) {
    }

    @PostMapping("/{incidentId}/close")
    public IncidentResponse close(@PathVariable UUID incidentId,
                                  @AuthenticatedUser UUID adminId,
                                  @Valid @RequestBody CloseIncidentRequest request) {
        return IncidentResponse.from(incidents.close(incidentId, adminId, request.notes()));
    }

    public record CloseIncidentRequest(
            @NotBlank(message = "Indica cómo se ha atendido la incidencia") String notes) {
    }

    public record IncidentResponse(
            UUID incidentId,
            UUID bookingId,
            UUID clientId,
            UUID sitterId,
            IncidentType type,
            IncidentStatus status,
            String description,
            String resolutionNotes,
            Instant openedAt,
            Instant resolvedAt) {

        static IncidentResponse from(Incident incident) {
            return new IncidentResponse(incident.id(), incident.bookingId(), incident.clientId(),
                    incident.sitterId(), incident.type(), incident.status(), incident.description(),
                    incident.resolutionNotes(), incident.openedAt(), incident.resolvedAt());
        }
    }
}
