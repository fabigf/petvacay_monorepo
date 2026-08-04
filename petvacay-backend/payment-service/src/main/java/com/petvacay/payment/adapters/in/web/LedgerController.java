package com.petvacay.payment.adapters.in.web;

import com.petvacay.payment.domain.model.BookingParties;
import com.petvacay.payment.domain.model.LedgerEntry;
import com.petvacay.payment.ports.out.BookingPartiesRepositoryPort;
import com.petvacay.payment.ports.out.LedgerRepositoryPort;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import com.petvacay.shared.domain.ResourceNotFoundException;
import com.petvacay.shared.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consulta del libro mayor de una reserva.
 *
 * <p>Contiene movimientos de dinero, asi que solo lo ven las dos partes de la
 * reserva y el equipo de administracion. Hasta el endurecimiento de la Fase 5
 * bastaba con estar autenticado y conocer el identificador de la reserva — un
 * UUID que no es ningun secreto — para leer cuanto cobro un cuidador cualquiera.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class LedgerController {

    private final LedgerRepositoryPort ledger;
    private final BookingPartiesRepositoryPort bookingParties;

    public LedgerController(LedgerRepositoryPort ledger, BookingPartiesRepositoryPort bookingParties) {
        this.ledger = ledger;
        this.bookingParties = bookingParties;
    }

    @GetMapping("/bookings/{bookingId}/ledger")
    public List<LedgerEntryResponse> ledgerOf(@PathVariable UUID bookingId,
                                              @AuthenticatedUser UUID actingUserId) {
        requireParticipant(bookingId, actingUserId);
        return ledger.findByBookingId(bookingId).stream().map(LedgerEntryResponse::from).toList();
    }

    /**
     * Acceso de soporte: el equipo de administracion arbitra disputas y necesita
     * ver los movimientos de cualquier reserva (negocio.md seccion 4.4).
     */
    @GetMapping("/admin/bookings/{bookingId}/ledger")
    @PreAuthorize("hasRole('ADMIN')")
    public List<LedgerEntryResponse> ledgerForSupport(@PathVariable UUID bookingId) {
        return ledger.findByBookingId(bookingId).stream().map(LedgerEntryResponse::from).toList();
    }

    private void requireParticipant(UUID bookingId, UUID actingUserId) {
        BookingParties parties = bookingParties.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("ERR_LEDGER_NOT_FOUND",
                        "No hay movimientos registrados para la reserva " + bookingId));

        if (!parties.includes(actingUserId)) {
            throw new BusinessRuleViolationException("ERR_NOT_BOOKING_PARTICIPANT", "Acceso denegado",
                    403, "Esta reserva no te pertenece.");
        }
    }

    public record LedgerEntryResponse(
            UUID entryId,
            LedgerEntry.LedgerEntryType type,
            UUID counterpartyId,
            BigDecimal amount,
            String currency,
            Instant occurredAt,
            String externalReference) {

        static LedgerEntryResponse from(LedgerEntry entry) {
            return new LedgerEntryResponse(entry.entryId(), entry.type(), entry.counterpartyId(),
                    entry.amount().amount(), entry.amount().currency().getCurrencyCode(),
                    entry.occurredAt(), entry.externalReference());
        }
    }
}
