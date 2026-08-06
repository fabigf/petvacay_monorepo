package com.petvacay.booking.adapters.in.web;

import com.petvacay.booking.adapters.in.web.BookingDtos.BookingDetailResponse;
import com.petvacay.booking.adapters.in.web.BookingDtos.CreateBookingRequest;
import com.petvacay.booking.adapters.in.web.BookingDtos.CreateBookingResponse;
import com.petvacay.booking.adapters.in.web.BookingDtos.HandoffResponse;
import com.petvacay.booking.adapters.in.web.BookingDtos.OpenDisputeRequest;
import com.petvacay.booking.adapters.in.web.BookingDtos.PetEmergencyRequest;
import com.petvacay.booking.domain.model.Booking;
import com.petvacay.booking.ports.in.CreateBookingUseCase;
import com.petvacay.booking.ports.in.ManageBookingUseCase;
import com.petvacay.booking.ports.in.QueryBookingUseCase;
import com.petvacay.booking.ports.out.ClientPetsPort;
import com.petvacay.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador de entrada REST del modulo de reservas.
 *
 * <p>Desde la Fase 5 la identidad del solicitante sale del {@code subject} de un
 * JWT validado por Spring Security, via {@code @AuthenticatedUser}: sustituye a
 * la cabecera {@code X-User-Id} de las Fases 1-4, que el cliente podia fijar a
 * cualquier valor y por tanto permitia suplantar a cualquier usuario.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final CreateBookingUseCase createBooking;
    private final ManageBookingUseCase manageBooking;
    private final QueryBookingUseCase queryBooking;

    public BookingController(CreateBookingUseCase createBooking,
                             ManageBookingUseCase manageBooking,
                             QueryBookingUseCase queryBooking) {
        this.createBooking = createBooking;
        this.manageBooking = manageBooking;
        this.queryBooking = queryBooking;
    }

    @PostMapping
    public ResponseEntity<CreateBookingResponse> create(@AuthenticatedUser UUID clientId,
                                                        @Valid @RequestBody CreateBookingRequest request) {
        Booking booking = createBooking.create(new CreateBookingUseCase.CreateBookingCommand(
                clientId, request.sitterId(), request.petIds(), request.startDate(),
                request.endDate(), request.serviceType()));

        return ResponseEntity.status(HttpStatus.CREATED).body(CreateBookingResponse.from(booking));
    }

    /**
     * Detalle de una reserva. Solo lo ven sus dos partes: ver el javadoc de
     * {@code QueryBookingUseCase.getById}.
     */
    @GetMapping("/{bookingId}")
    public BookingDetailResponse getById(@PathVariable UUID bookingId,
                                         @AuthenticatedUser UUID actingUserId) {
        return BookingDetailResponse.from(queryBooking.getById(bookingId, actingUserId));
    }

    /**
     * Plazas libres de un cuidador noche a noche, para que el cliente vea la
     * disponibilidad ANTES de enviar la solicitud en vez de descubrirla con un
     * 409 al confirmarla.
     *
     * <p>Cuelga de {@code /bookings} y no del buscador porque la respuesta la
     * calcula quien luego va a validar la reserva: asi lo que se enseña y lo que
     * se aplica no pueden discrepar.
     *
     * <p>Va declarado ANTES que {@code /{bookingId}} porque Spring resuelve la
     * ruta literal frente a la variable, pero dejarlo explicito evita que un
     * cambio futuro de orden lo convierta en "una reserva llamada availability".
     */
    @GetMapping("/availability")
    public List<QueryBookingUseCase.DailyAvailability> availability(
            @RequestParam UUID sitterId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return queryBooking.availability(sitterId, startDate, endDate);
    }

    /**
     * Fichas de las mascotas de la reserva. Van en su propio recurso y no dentro
     * del detalle porque viven en otro servicio: incrustarlas obligaria a que
     * cada lectura de una reserva — incluido el listado — esperase a una llamada
     * HTTP a identity-service que la mayoria de las pantallas no necesitan.
     */
    @GetMapping("/{bookingId}/pets")
    public List<ClientPetsPort.PetDetails> pets(@PathVariable UUID bookingId,
                                                @AuthenticatedUser UUID actingUserId) {
        return queryBooking.listPetsOfBooking(bookingId, actingUserId);
    }

    /**
     * Dirección exacta del cuidador y franja de entrega/recogida (negocio.md 4.2
     * y 5). En su propio recurso por el mismo motivo que las mascotas: solo hace
     * falta al ver el detalle de una reserva ya pagada, no en cada lectura.
     */
    @GetMapping("/{bookingId}/handoff")
    public HandoffResponse handoff(@PathVariable UUID bookingId,
                                   @AuthenticatedUser UUID actingUserId) {
        return HandoffResponse.from(queryBooking.getHandoffInfo(bookingId, actingUserId));
    }

    /** Historial de reservas del usuario, como cliente o como cuidador. */
    @GetMapping
    public List<BookingDetailResponse> list(@AuthenticatedUser UUID userId,
                                            @RequestParam(defaultValue = "client") String role) {
        List<Booking> bookings = "sitter".equalsIgnoreCase(role)
                ? queryBooking.listBySitter(userId)
                : queryBooking.listByClient(userId);
        return bookings.stream().map(BookingDetailResponse::from).toList();
    }

    @PostMapping("/{bookingId}/accept")
    public BookingDetailResponse accept(@PathVariable UUID bookingId,
                                        @AuthenticatedUser UUID actingUserId) {
        return BookingDetailResponse.from(manageBooking.acceptBySitter(bookingId, actingUserId));
    }

    @PostMapping("/{bookingId}/reject")
    public BookingDetailResponse reject(@PathVariable UUID bookingId,
                                        @AuthenticatedUser UUID actingUserId) {
        return BookingDetailResponse.from(manageBooking.rejectBySitter(bookingId, actingUserId));
    }

    /**
     * Confirmacion de pago. En Fase 6 dejara de exponerse al cliente y la invocara
     * el webhook de Stripe en el payment-service.
     */
    @PostMapping("/{bookingId}/confirm-payment")
    public BookingDetailResponse confirmPayment(@PathVariable UUID bookingId,
                                                @AuthenticatedUser UUID actingUserId) {
        return BookingDetailResponse.from(manageBooking.confirmPayment(bookingId, actingUserId));
    }

    @PostMapping("/{bookingId}/start")
    public BookingDetailResponse start(@PathVariable UUID bookingId,
                                       @AuthenticatedUser UUID actingUserId) {
        return BookingDetailResponse.from(manageBooking.start(bookingId, actingUserId));
    }

    @PostMapping("/{bookingId}/complete")
    public BookingDetailResponse complete(@PathVariable UUID bookingId,
                                          @AuthenticatedUser UUID actingUserId) {
        return BookingDetailResponse.from(manageBooking.complete(bookingId, actingUserId));
    }

    /**
     * Cancela la reserva.
     *
     * <p>Ya no acepta un parametro {@code actor}: hasta la Fase 5 el cliente podia
     * declararse cuidador en la propia peticion y activar asi la politica de
     * reembolso integro (incluidas las tasas) en lugar de la que le correspondia,
     * ademas de penalizar injustamente al cuidador. Ahora el rol se deduce en el
     * servidor de quien esta autenticado.
     */
    @PostMapping("/{bookingId}/cancel")
    public BookingDetailResponse cancel(@PathVariable UUID bookingId,
                                        @AuthenticatedUser UUID actingUserId) {
        return BookingDetailResponse.from(manageBooking.cancel(bookingId, actingUserId));
    }

    /**
     * Botón de alerta médica (negocio.md 5). Devuelve la reserva sin cambios de
     * estado: la alerta no altera el ciclo de vida, solo dispara el aviso urgente
     * al dueño y al equipo de soporte.
     */
    @PostMapping("/{bookingId}/emergency")
    public BookingDetailResponse raiseEmergency(@PathVariable UUID bookingId,
                                                @AuthenticatedUser UUID actingUserId,
                                                @Valid @RequestBody PetEmergencyRequest request) {
        return BookingDetailResponse.from(
                manageBooking.raisePetEmergency(bookingId, actingUserId, request.description()));
    }

    @PostMapping("/{bookingId}/disputes")
    public BookingDetailResponse openDispute(@PathVariable UUID bookingId,
                                             @AuthenticatedUser UUID openedBy,
                                             @Valid @RequestBody OpenDisputeRequest request) {
        return BookingDetailResponse.from(manageBooking.openDispute(bookingId, openedBy, request.reason()));
    }
}
