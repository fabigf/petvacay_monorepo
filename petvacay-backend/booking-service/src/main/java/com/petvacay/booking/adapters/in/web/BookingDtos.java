package com.petvacay.booking.adapters.in.web;

import com.petvacay.booking.domain.model.Booking;
import com.petvacay.booking.domain.model.BookingStatus;
import com.petvacay.booking.domain.model.CancellationActor;
import com.petvacay.booking.domain.model.DisputeResolution;
import com.petvacay.booking.domain.model.ServiceType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contratos JSON del modulo de reservas (especificaciones.md seccion 1.2). */
public final class BookingDtos {

    private BookingDtos() {
    }

    public record CreateBookingRequest(
            @NotNull(message = "El cuidador es obligatorio") UUID sitterId,
            @NotEmpty(message = "Debes seleccionar al menos una mascota") List<UUID> petIds,
            @NotNull(message = "La fecha de inicio es obligatoria")
            @Future(message = "La fecha de inicio debe ser futura") LocalDate startDate,
            @NotNull(message = "La fecha de fin es obligatoria") LocalDate endDate,
            @NotNull(message = "El tipo de servicio es obligatorio") ServiceType serviceType) {
    }

    /** Respuesta 201 con la forma exacta esperada por el frontend. */
    public record CreateBookingResponse(UUID bookingId, BookingStatus status, BigDecimal finalAmountToPay) {

        public static CreateBookingResponse from(Booking booking) {
            return new CreateBookingResponse(booking.id(), booking.status(),
                    booking.pricing().totalChargedToClient().amount());
        }
    }

    public record BookingDetailResponse(
            UUID bookingId,
            UUID clientId,
            UUID sitterId,
            List<UUID> petIds,
            LocalDate startDate,
            LocalDate endDate,
            long nights,
            ServiceType serviceType,
            BookingStatus status,
            String currency,
            BigDecimal pricePerNight,
            BigDecimal basePrice,
            BigDecimal clientServiceFee,
            BigDecimal finalAmountToPay,
            BigDecimal sitterPayout,
            BigDecimal platformRevenue,
            Instant requestedAt,
            Instant confirmedAt,
            Instant closedAt,
            Instant scheduledPayoutAt,
            CancellationActor cancelledBy,
            BigDecimal refundToClient,
            String disputeReason,
            DisputeResolution disputeResolution,
            String disputeResolutionNotes) {

        public static BookingDetailResponse from(Booking booking) {
            return new BookingDetailResponse(
                    booking.id(),
                    booking.clientId(),
                    booking.sitterId(),
                    booking.petIds(),
                    booking.dates().start(),
                    booking.dates().end(),
                    booking.dates().nights(),
                    booking.serviceType(),
                    booking.status(),
                    booking.pricing().basePrice().currency().getCurrencyCode(),
                    booking.pricePerNight().amount(),
                    booking.pricing().basePrice().amount(),
                    booking.pricing().clientServiceFee().amount(),
                    booking.pricing().totalChargedToClient().amount(),
                    booking.pricing().sitterPayout().amount(),
                    booking.pricing().platformRevenue().amount(),
                    booking.requestedAt(),
                    booking.confirmedAt(),
                    booking.closedAt(),
                    booking.scheduledPayoutAt(),
                    booking.cancelledBy(),
                    booking.cancellationOutcome() == null
                            ? null : booking.cancellationOutcome().refundToClient().amount(),
                    booking.disputeReason(),
                    booking.disputeResolution(),
                    booking.disputeResolutionNotes());
        }
    }

    public record PetEmergencyRequest(
            @NotBlank(message = "Describe brevemente qué le ocurre a la mascota") String description) {
    }

    public record OpenDisputeRequest(@NotNull(message = "El motivo es obligatorio") String reason) {
    }

    /**
     * Resolucion manual de una disputa. {@code refundToClient} solo se lee cuando
     * la resolucion es {@code SPLIT}: en los dos extremos el importe lo deduce el
     * servidor de lo que realmente se cobro.
     */
    public record ResolveDisputeRequest(
            @NotNull(message = "La resolución es obligatoria") DisputeResolution resolution,
            @PositiveOrZero(message = "El reembolso no puede ser negativo") BigDecimal refundToClient,
            @NotBlank(message = "Hay que dejar constancia del motivo de la resolución") String notes) {
    }
}
