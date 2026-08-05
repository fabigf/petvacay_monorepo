package com.petvacay.booking.adapters.in.web;

import com.petvacay.booking.adapters.in.web.BookingDtos.BookingDetailResponse;
import com.petvacay.booking.adapters.in.web.BookingDtos.ResolveDisputeRequest;
import com.petvacay.booking.domain.model.BookingStatus;
import com.petvacay.booking.ports.in.ManageBookingUseCase;
import com.petvacay.booking.ports.in.QueryBookingUseCase;
import com.petvacay.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Back-office de reservas (negocio.md 2.3 y 4.4).
 *
 * <p>Va en su propio controlador y no en {@link BookingController} porque su
 * regla de acceso es la contraria: aqui **no** se exige ser parte de la reserva,
 * se exige ser administrador de la plataforma. Mezclar ambas cosas en la misma
 * clase invita a que una operacion nueva herede la comprobacion equivocada; la
 * ruta {@code /api/v1/admin/**} la protege {@code SecurityConfig} con
 * {@code hasRole("ADMIN")}.
 */
@RestController
@RequestMapping("/api/v1/admin/bookings")
public class AdminBookingController {

    private final QueryBookingUseCase queryBooking;
    private final ManageBookingUseCase manageBooking;

    public AdminBookingController(QueryBookingUseCase queryBooking, ManageBookingUseCase manageBooking) {
        this.queryBooking = queryBooking;
        this.manageBooking = manageBooking;
    }

    /**
     * Reservas filtradas por estado. Por defecto {@code EN_DISPUTA}: es el caso
     * para el que existe este listado — sin el, un administrador solo podria
     * atender una incidencia si ya conociera de memoria su identificador.
     */
    @GetMapping
    public List<BookingDetailResponse> list(
            @RequestParam(defaultValue = "EN_DISPUTA") BookingStatus status) {
        return queryBooking.listByStatus(status).stream().map(BookingDetailResponse::from).toList();
    }

    @PostMapping("/{bookingId}/resolve-dispute")
    public BookingDetailResponse resolveDispute(@PathVariable UUID bookingId,
                                                @AuthenticatedUser UUID adminId,
                                                @Valid @RequestBody ResolveDisputeRequest request) {
        return BookingDetailResponse.from(manageBooking.resolveDispute(
                new ManageBookingUseCase.ResolveDisputeCommand(bookingId, adminId, request.resolution(),
                        request.refundToClient(), request.notes())));
    }
}
