package com.petvacay.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petvacay.booking.domain.model.ServiceType;
import com.petvacay.booking.ports.out.ClientPetsPort;
import com.petvacay.booking.ports.out.SitterCatalogPort;
import com.petvacay.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el modulo de reservas como servicio independiente (Fase 4): la
 * maquina de estados, el bloqueo de capacidad y el mapeo de errores a RFC 7807,
 * todo sobre la API REST real de booking-service.
 *
 * <p>Antes de la Fase 4, esta cobertura vivia en un unico test de extremo a
 * extremo que arrancaba el monolito completo (identity + booking + search +
 * payment en el mismo proceso). Ahora que {@code SitterCatalogPort} se resuelve
 * por HTTP contra identity-service, un test de booking-service en solitario no
 * puede depender de que ese servicio este arrancado: se sustituye por un doble
 * de prueba ({@link FakeSitterCatalog}) inyectado en el limite del puerto, la
 * tecnica estandar para probar un microservicio sin levantar sus vecinos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("booking-service: API REST y mapeo de errores")
class BookingServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeSitterCatalog sitterCatalog;

    @Autowired
    private FakeClientPets clientPets;

    @Autowired
    private MutableClock clock;

    private final UUID clientId = UUID.randomUUID();
    private final UUID petId = UUID.randomUUID();
    private UUID sitterId;

    private final LocalDate startDate = LocalDate.now().plusDays(30);
    private final LocalDate endDate = startDate.plusDays(7);

    @BeforeEach
    void setUp() {
        sitterId = UUID.randomUUID();
        sitterCatalog.register(sitterId, "Carlos", true, "25.00", 1);
        clientPets.register(clientId, petId);
        // El contexto de Spring se comparte entre tests: un reloj adelantado por
        // el test anterior falsearia las reglas temporales del siguiente.
        clock.reset();
    }

    @Test
    @DisplayName("la disponibilidad refleja las plazas ocupadas por una reserva confirmada")
    void availabilityReflectsConfirmedBookings() throws Exception {
        // El cuidador de pruebas tiene una sola plaza: al confirmarse la reserva,
        // esas noches se quedan sin ninguna libre.
        mockMvc.perform(get("/api/v1/bookings/availability")
                        .with(asClient())
                        .param("sitterId", sitterId.toString())
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].capacity").value(1))
                .andExpect(jsonPath("$[0].occupied").value(0));

        createConfirmedBooking();

        mockMvc.perform(get("/api/v1/bookings/availability")
                        .with(asClient())
                        .param("sitterId", sitterId.toString())
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].occupied").value(1));

        // Lo que dice la disponibilidad y lo que aplica la validacion tienen que
        // ser lo mismo: sin plazas libres, la reserva se rechaza.
        mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_BOOKING_DATE_CONFLICT"));
    }

    @Test
    @DisplayName("no deja reservar con la mascota de otra persona")
    void cannotBookWithSomeoneElsesPet() throws Exception {
        String payload = """
                {
                  "sitterId": "%s",
                  "petIds": ["%s"],
                  "startDate": "%s",
                  "endDate": "%s",
                  "serviceType": "NIGHT_ACCOMMODATION"
                }
                """.formatted(sitterId, UUID.randomUUID(), startDate, endDate);

        mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_PET_NOT_OWNED"));
    }

    @Test
    @DisplayName("crea la reserva, la acepta y confirma el pago con el desglose correcto")
    void fullBookingFlow() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SOLICITADO"))
                .andExpect(jsonPath("$.finalAmountToPay").value(183.75))
                .andReturn();

        String bookingId = readJson(created).get("bookingId").asText();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept").with(asSitter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDIENTE_PAGO"));

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/confirm-payment").with(asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.sitterPayout").value(148.75));
    }

    @Test
    @DisplayName("una reserva contra un cuidador no verificado se rechaza con 409")
    void unverifiedSitterIsRejected() throws Exception {
        UUID unverified = UUID.randomUUID();
        sitterCatalog.register(unverified, "Elena", false, "20.00", 1);

        String payload = """
                {
                  "sitterId": "%s",
                  "petIds": ["%s"],
                  "startDate": "%s",
                  "endDate": "%s",
                  "serviceType": "NIGHT_ACCOMMODATION"
                }
                """.formatted(unverified, petId, startDate, endDate);

        mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_SITTER_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("una segunda reserva solapada devuelve 409 con formato RFC 7807")
    void overlappingBookingReturnsProblemDetail() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload()))
                .andExpect(status().isCreated())
                .andReturn();

        String bookingId = readJson(created).get("bookingId").asText();
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept").with(asSitter())).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/confirm-payment").with(asClient())).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_BOOKING_DATE_CONFLICT"))
                .andExpect(jsonPath("$.title").value("Cuidador No Disponible"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("un formulario invalido devuelve 400 con invalid_params por campo")
    void invalidPayloadReturnsFieldErrors() throws Exception {
        String payload = """
                {
                  "sitterId": "%s",
                  "petIds": [],
                  "startDate": "2020-01-01",
                  "endDate": "2020-01-05",
                  "serviceType": "NIGHT_ACCOMMODATION"
                }
                """.formatted(sitterId);

        mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalid_params").isArray());
    }

    @Test
    @DisplayName("un JSON con tipos incorrectos devuelve 400, no 500 (GlobalExceptionHandler de common)")
    void malformedBodyIsClientError() throws Exception {
        String payload = """
                {
                  "sitterId": ["%s", "%s"],
                  "petIds": ["%s"],
                  "startDate": "%s",
                  "endDate": "%s",
                  "serviceType": "NIGHT_ACCOMMODATION"
                }
                """.formatted(sitterId, sitterId, petId, startDate, endDate);

        mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("un identificador con formato invalido devuelve 400, no 500")
    void invalidPathParameterIsClientError() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/no-es-un-uuid/accept").with(asClient()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("una ruta inexistente devuelve 404, no 500")
    void unknownRouteIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/rutaquenoexiste").with(asClient()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_ROUTE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- seguridad

    @Test
    @DisplayName("sin token, cualquier operacion de reserva devuelve 401 en RFC 7807")
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHENTICATED"))
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }

    @Test
    @DisplayName("consultar el historial sin token tambien devuelve 401")
    void anonymousListIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHENTICATED"));
    }

    /**
     * La identidad ya no puede falsificarse desde el cliente: la reserva se crea
     * a nombre del {@code subject} del token, no de lo que diga el cuerpo o una
     * cabecera. Este test fija esa garantia, que es justamente lo que la cabecera
     * {@code X-User-Id} de las Fases 1-4 no ofrecia.
     */
    @Test
    @DisplayName("la reserva se atribuye al subject del token, no a una cabecera manipulable")
    void bookingIsAttributedToTokenSubject() throws Exception {
        UUID otroUsuario = UUID.randomUUID();

        MvcResult created = mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        // Intento de suplantacion: se ignora por completo.
                        .header("X-User-Id", otroUsuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload()))
                .andExpect(status().isCreated())
                .andReturn();

        String bookingId = readJson(created).get("bookingId").asText();

        mockMvc.perform(get("/api/v1/bookings/" + bookingId).with(asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientId.toString()));
    }

    /**
     * Un tercero autenticado no puede tocar una reserva que no es suya, aunque
     * conozca su identificador: los UUID no son secretos y acaban en logs, en la
     * barra de direcciones o en capturas de pantalla.
     */
    @Test
    @DisplayName("un usuario ajeno a la reserva no puede cancelarla ni aceptarla")
    void strangerCannotActOnSomeoneElsesBooking() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel").with(asStranger()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_NOT_BOOKING_PARTICIPANT"));

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept").with(asStranger()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_NOT_BOOKING_PARTICIPANT"));
    }

    @Test
    @DisplayName("el cliente no puede aceptar su propia reserva haciendose pasar por el cuidador")
    void clientCannotAcceptOwnBooking() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload()))
                .andExpect(status().isCreated())
                .andReturn();

        String bookingId = readJson(created).get("bookingId").asText();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept").with(asClient()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_WRONG_BOOKING_ROLE"));
    }

    /**
     * Fija el cierre de un exploit financiero: hasta la Fase 5 el endpoint de
     * cancelacion aceptaba {@code ?actor=SITTER}, con lo que un cliente podia
     * activar la politica de "cancela el cuidador" — reembolso del 100% incluidas
     * las tasas — en lugar de la suya. Ahora el rol se deduce del token y el
     * parametro se ignora por completo.
     */
    @Test
    @DisplayName("el cliente no puede elegir la politica de reembolso via ?actor=SITTER")
    void clientCannotForgeCancellationActor() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .with(asClient())
                        .param("actor", "SITTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"))
                // Se aplica la politica del CLIENTE con mas de 48h de antelacion:
                // devuelve la tarifa base (175,00) y retiene la tasa de gestion.
                // Si el parametro se hubiera respetado, la politica habria sido la
                // del cuidador y el reembolso los 183,75 integros, tasas incluidas.
                .andExpect(jsonPath("$.refundToClient").value(175.00));
    }

    @Test
    @DisplayName("la solicitud deja un aviso al cuidador y aceptarla deja otro al cliente")
    void bookingTransitionsFeedTheNotificationBell() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload()))
                .andExpect(status().isCreated())
                .andReturn();
        String bookingId = readJson(created).get("bookingId").asText();

        mockMvc.perform(get("/api/v1/notifications").with(asSitter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("BOOKING_REQUESTED"))
                .andExpect(jsonPath("$[0].unread").value(true));

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept").with(asSitter()))
                .andExpect(status().isOk());

        MvcResult clientFeed = mockMvc.perform(get("/api/v1/notifications").with(asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("BOOKING_ACCEPTED"))
                .andReturn();

        String notificationId = readJson(clientFeed).get(0).get("notificationId").asText();
        mockMvc.perform(post("/api/v1/notifications/" + notificationId + "/read").with(asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(false));

        // El buzon es de cada uno: el aviso del cliente no lo puede leer nadie mas.
        mockMvc.perform(post("/api/v1/notifications/" + notificationId + "/read").with(asStranger()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_NOT_NOTIFICATION_RECIPIENT"));
    }

    @Test
    @DisplayName("una alerta médica y una disputa quedan registradas como incidencias del back-office")
    void incidentsAreRecordedForTheBackOffice() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/emergency")
                        .with(asSitter())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Cojea de la pata trasera\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/disputes")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Volvió con una herida\"}"))
                .andExpect(status().isOk());

        // Se filtra por reserva en vez de contar el total: los tests comparten la
        // misma base de datos en memoria y los de al lado tambien dejan
        // incidencias abiertas.
        mockMvc.perform(get("/api/v1/admin/incidents").param("status", "ABIERTA").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(byBooking(bookingId), hasSize(2)))
                // Cada incidencia sabe a quien afecta sin cruzarla con la reserva.
                .andExpect(jsonPath(byBooking(bookingId) + ".clientId",
                        everyItem(is(clientId.toString()))))
                .andExpect(jsonPath(byBooking(bookingId) + ".sitterId",
                        everyItem(is(sitterId.toString()))));
    }

    /**
     * Filtro JSONPath de las incidencias de una reserva concreta dentro de la
     * pagina devuelta. Se filtra por reserva en vez de contar el total porque los
     * tests comparten la base de datos en memoria.
     */
    private static String byBooking(String bookingId) {
        return "$.content[?(@.bookingId=='" + bookingId + "')]";
    }

    @Test
    @DisplayName("resolver la disputa cierra su incidencia sin dejarla pendiente en el panel")
    void resolvingADisputeClosesItsIncident() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/disputes")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Volvió con una herida\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/resolve-dispute")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"FAVOR_CLIENT\",\"notes\":\"Informe veterinario\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZADO"));

        mockMvc.perform(get("/api/v1/admin/incidents").param("status", "ABIERTA").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(byBooking(bookingId), hasSize(0)));
    }

    @Test
    @DisplayName("la dirección de entrega no está disponible hasta que el pago se confirma")
    void handoffNotAvailableBeforePayment() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload()))
                .andExpect(status().isCreated())
                .andReturn();
        String bookingId = readJson(created).get("bookingId").asText();

        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/handoff").with(asClient()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_HANDOFF_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("tras confirmar el pago, ambas partes ven la dirección y la franja de entrega")
    void handoffAvailableAfterPayment() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/handoff").with(asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sitterAddress").value("Calle Falsa 123, Springfield"))
                .andExpect(jsonPath("$.dropOffDate").value(startDate.toString()))
                .andExpect(jsonPath("$.pickUpDate").value(endDate.toString()))
                .andExpect(jsonPath("$.windowStart").value("09:00:00"))
                .andExpect(jsonPath("$.windowEnd").value("20:00:00"));

        // El cuidador tambien puede consultarla: por ejemplo para confirmar la
        // franja que se le ha comunicado al cliente.
        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/handoff").with(asSitter()))
                .andExpect(status().isOk());

        // Ajena a la reserva: acceso denegado, igual que con las mascotas.
        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/handoff").with(asStranger()))
                .andExpect(status().isForbidden());
    }

    /**
     * Regresion de una fuga real: {@code GET /bookings/{id}} no comprobaba nada
     * y devolvia el detalle completo a cualquier usuario autenticado que
     * conociera el identificador — las dos partes, las mascotas, el desglose
     * economico y las notas de la disputa. Las transiciones si lo comprobaban;
     * la lectura se habia quedado fuera.
     */
    @Test
    @DisplayName("un usuario ajeno a la reserva no puede leer su detalle aunque sepa el id")
    void strangerCannotReadSomeoneElsesBooking() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(get("/api/v1/bookings/" + bookingId).with(asStranger()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_NOT_BOOKING_PARTICIPANT"));

        // Las dos partes si la ven: la restriccion no puede romper el caso normal.
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).with(asClient()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).with(asSitter()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("leer el detalle de una reserva sin token devuelve 401")
    void anonymousDetailIsUnauthorized() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(get("/api/v1/bookings/" + bookingId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("el historial de cada usuario solo contiene sus propias reservas")
    void historyIsScopedToTheRequester() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(get("/api/v1/bookings").with(asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.bookingId=='" + bookingId + "')]", hasSize(1)));

        mockMvc.perform(get("/api/v1/bookings").param("role", "sitter").with(asSitter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.bookingId=='" + bookingId + "')]", hasSize(1)));

        // Un tercero no la ve por ninguna de las dos vias.
        mockMvc.perform(get("/api/v1/bookings").with(asStranger()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.bookingId=='" + bookingId + "')]", hasSize(0)));
        mockMvc.perform(get("/api/v1/bookings").param("role", "sitter").with(asStranger()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.bookingId=='" + bookingId + "')]", hasSize(0)));
    }

    /**
     * El back-office no pasa por {@code requireParticipant} — un administrador no
     * es parte de ninguna reserva — asi que su unica barrera es el rol. Sin este
     * test, restringir la lectura por participante podria haberse "arreglado"
     * dejando el listado de disputas abierto a cualquiera.
     */
    @Test
    @DisplayName("el back-office de reservas exige rol de administrador")
    void adminBookingRoutesRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings").with(asClient()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/bookings").with(asAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("resolver una disputa tambien exige rol de administrador")
    void resolvingDisputeRequiresAdminRole() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/disputes")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Volvió con una herida\"}"))
                .andExpect(status().isOk());

        // Ni el cliente ni el cuidador pueden dictar el reparto de su propia disputa.
        mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/resolve-dispute")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"FAVOR_CLIENT\",\"notes\":\"me lo devuelvo\"}"))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------------- reseñas

    @Test
    @DisplayName("el cliente valora la reserva finalizada y la reseña sale en la vitrina del cuidador")
    void clientCanReviewFinishedBooking() throws Exception {
        String bookingId = createFinishedBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/reviews")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Cuidó de Toby estupendamente\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.authorRole").value("CLIENT"));

        mockMvc.perform(get("/api/v1/reviews/sitters/" + sitterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].comment").value("Cuidó de Toby estupendamente"));
    }

    @Test
    @DisplayName("no se puede valorar una reserva que aun no ha finalizado")
    void cannotReviewBeforeTheServiceEnds() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/reviews")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Todo bien\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_REVIEW_BOOKING_NOT_FINISHED"));
    }

    @Test
    @DisplayName("solo se admite una reseña por reserva")
    void cannotReviewTheSameBookingTwice() throws Exception {
        String bookingId = createFinishedBooking();
        String payload = "{\"rating\":4,\"comment\":\"Muy atento\"}";

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/reviews")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/reviews")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_REVIEW_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("un tercero no puede valorar una reserva ajena")
    void strangerCannotReviewSomeoneElsesBooking() throws Exception {
        String bookingId = createFinishedBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/reviews")
                        .with(asStranger())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":1,\"comment\":\"Ni lo conozco\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_NOT_BOOKING_PARTICIPANT"));
    }

    @Test
    @DisplayName("una puntuacion fuera de 1..5 se rechaza con 400 por campo")
    void reviewRatingIsValidated() throws Exception {
        String bookingId = createFinishedBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/reviews")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":9,\"comment\":\"Fuera de rango\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("las reseñas de una reserva solo las leen sus dos partes")
    void bookingReviewsAreForItsParticipantsOnly() throws Exception {
        String bookingId = createFinishedBooking();

        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/reviews").with(asClient()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/reviews").with(asSitter()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/reviews").with(asStranger()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_NOT_BOOKING_PARTICIPANT"));
    }

    // --------------------------------------------------- disponibilidad y plazas

    /**
     * El calendario del frontend bloquea una noche cuando {@code free < mascotas
     * seleccionadas}. Este test fija el caso que motivo esa pantalla: con tres
     * plazas y dos ya ocupadas, reservar para dos mascotas no cabe y para una si.
     */
    @Test
    @DisplayName("con 3 plazas y 2 ocupadas, caben 1 mascota mas pero no 2")
    void availabilityRespectsRemainingSlots() throws Exception {
        UUID roomy = UUID.randomUUID();
        sitterCatalog.register(roomy, "Marta", true, "20.00", 3);

        UUID secondPet = UUID.randomUUID();
        clientPets.register(clientId, petId, secondPet);

        // Reserva confirmada que ocupa 2 de las 3 plazas.
        String twoPets = """
                {
                  "sitterId": "%s",
                  "petIds": ["%s", "%s"],
                  "startDate": "%s",
                  "endDate": "%s",
                  "serviceType": "NIGHT_ACCOMMODATION"
                }
                """.formatted(roomy, petId, secondPet, startDate, endDate);

        MvcResult created = mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoPets))
                .andExpect(status().isCreated())
                .andReturn();
        String bookingId = readJson(created).get("bookingId").asText();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept").with(tokenFor(roomy, "ROLE_SITTER")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/confirm-payment").with(asClient()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings/availability")
                        .with(asClient())
                        .param("sitterId", roomy.toString())
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].capacity").value(3))
                .andExpect(jsonPath("$[0].occupied").value(2))
                // El calendario del frontend decide con este campo — no recalcula
                // capacity - occupied por su cuenta — asi que tiene que viajar en
                // el JSON. Si dejara de hacerlo, el navegador leeria `undefined`,
                // la comparacion `free < mascotas` seria falsa y TODOS los dias
                // apareceran libres: el fallo mas silencioso posible.
                .andExpect(jsonPath("$[0].free").value(1));

        // Queda una plaza: una mascota entra.
        UUID thirdPet = UUID.randomUUID();
        UUID otherClient = UUID.randomUUID();
        clientPets.register(otherClient, thirdPet);

        String onePet = """
                {
                  "sitterId": "%s",
                  "petIds": ["%s"],
                  "startDate": "%s",
                  "endDate": "%s",
                  "serviceType": "NIGHT_ACCOMMODATION"
                }
                """.formatted(roomy, thirdPet, startDate, endDate);

        mockMvc.perform(post("/api/v1/bookings")
                        .with(tokenFor(otherClient, "ROLE_CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(onePet))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("pedir mas plazas de las libres se rechaza con 409")
    void bookingMorePetsThanFreeSlotsIsRejected() throws Exception {
        UUID roomy = UUID.randomUUID();
        sitterCatalog.register(roomy, "Marta", true, "20.00", 3);

        UUID secondPet = UUID.randomUUID();
        UUID thirdPet = UUID.randomUUID();
        UUID fourthPet = UUID.randomUUID();
        clientPets.register(clientId, petId, secondPet, thirdPet, fourthPet);

        String fourPets = """
                {
                  "sitterId": "%s",
                  "petIds": ["%s", "%s", "%s", "%s"],
                  "startDate": "%s",
                  "endDate": "%s",
                  "serviceType": "NIGHT_ACCOMMODATION"
                }
                """.formatted(roomy, petId, secondPet, thirdPet, fourthPet, startDate, endDate);

        mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fourPets))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_BOOKING_DATE_CONFLICT"));
    }

    @Test
    @DisplayName("la disponibilidad de un cuidador inexistente devuelve 404, no una lista vacia")
    void availabilityOfUnknownSitterIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/availability")
                        .with(asClient())
                        .param("sitterId", UUID.randomUUID().toString())
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_SITTER_NOT_FOUND"));
    }

    @Test
    @DisplayName("la entrega incluye las coordenadas del cuidador para los enlaces de mapa")
    void handoffCarriesSitterCoordinates() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/handoff").with(asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sitterLatitude").value(40.0))
                .andExpect(jsonPath("$.sitterLongitude").value(-3.0));
    }

    @Test
    @DisplayName("el cuidador no puede valorar: solo lee las reseñas que recibe")
    void sitterCannotLeaveReviews() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/reviews")
                        .with(asSitter())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Buen cliente\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_SITTER_CANNOT_REVIEW"));
    }

    @Test
    @DisplayName("la alerta médica del cuidador avisa al dueño sin tocar el estado de la reserva")
    void petEmergencyNotifiesTheOwner() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/emergency")
                        .with(asSitter())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Cojea de la pata trasera\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADO"));

        mockMvc.perform(get("/api/v1/notifications").with(asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("PET_EMERGENCY"));

        // Solo el cuidador tiene el boton: es quien esta con el animal.
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/emergency")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"prueba\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("cancelar una reserva confirmada le cuesta al cuidador una reseña de 1 estrella del sistema")
    void sitterCancellationCreatesSystemReview() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel").with(asSitter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        mockMvc.perform(get("/api/v1/reviews/sitters/" + sitterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].authorRole").value("SYSTEM"))
                .andExpect(jsonPath("$[0].rating").value(1));
    }

    @Test
    @DisplayName("cancelar el cliente no genera ninguna reseña automatica contra el cuidador")
    void clientCancellationDoesNotPenalizeSitter() throws Exception {
        String bookingId = createConfirmedBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel").with(asClient()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reviews/sitters/" + sitterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** Reserva confirmada y pagada, punto de partida de las pruebas de cancelacion. */
    private String createConfirmedBooking() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/bookings")
                        .with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload()))
                .andExpect(status().isCreated())
                .andReturn();

        String bookingId = readJson(created).get("bookingId").asText();
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept").with(asSitter()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/confirm-payment").with(asClient()))
                .andExpect(status().isOk());
        return bookingId;
    }

    /**
     * Reserva ya finalizada, punto de partida de las pruebas de resenas: solo se
     * puede valorar un servicio que de verdad ha terminado.
     *
     * <p>Adelanta el reloj en dos saltos porque el dominio exige los dos
     * hitos por separado — no se puede iniciar un servicio antes de su fecha ni
     * liberar los fondos antes de que cierre la ventana de disputa de 24h.
     */
    private String createFinishedBooking() throws Exception {
        String bookingId = createConfirmedBooking();

        clock.setTo(startDate.atStartOfDay().toInstant(ZoneOffset.UTC));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/start").with(asSitter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_PROGRESO"));

        clock.setTo(endDate.plusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/complete").with(asSitter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZADO"));

        return bookingId;
    }

    /** Token valido del cliente de pruebas: el subject es lo que identifica al usuario. */
    private JwtRequestPostProcessor asClient() {
        return tokenFor(clientId, "ROLE_CLIENT");
    }

    private JwtRequestPostProcessor asSitter() {
        return tokenFor(sitterId, "ROLE_SITTER");
    }

    /** Usuario perfectamente autenticado, pero ajeno a la reserva. */
    private JwtRequestPostProcessor asStranger() {
        return tokenFor(UUID.randomUUID(), "ROLE_CLIENT");
    }

    /** Administrador de la plataforma: no es parte de ninguna reserva concreta. */
    private JwtRequestPostProcessor asAdmin() {
        return tokenFor(UUID.randomUUID(), "ROLE_ADMIN");
    }

    private JwtRequestPostProcessor tokenFor(UUID userId, String role) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private String bookingPayload() {
        return """
                {
                  "sitterId": "%s",
                  "petIds": ["%s"],
                  "startDate": "%s",
                  "endDate": "%s",
                  "serviceType": "NIGHT_ACCOMMODATION"
                }
                """.formatted(sitterId, petId, startDate, endDate);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body).isNotBlank();
        return objectMapper.readTree(body);
    }

    @TestConfiguration
    static class TestCatalogConfig {

        @Bean
        @Primary
        FakeSitterCatalog fakeSitterCatalog() {
            return new FakeSitterCatalog();
        }

        @Bean
        @Primary
        FakeClientPets fakeClientPets() {
            return new FakeClientPets();
        }

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    /**
     * Reloj que el test puede adelantar.
     *
     * <p>Hace falta para llegar a {@code FINALIZADO} por la API real: el dominio
     * exige que el servicio haya empezado y que hayan pasado 24h desde su fin
     * antes de liberar los fondos, y esas fechas son futuras a proposito (una
     * reserva se solicita con antelacion). Adelantar el reloj es la unica forma
     * de recorrer ese camino sin duplicar aqui las reglas temporales que
     * {@code BookingLifecycleTest} ya cubre a nivel de dominio.
     *
     * <p>Mientras nadie lo fije se comporta como el reloj del sistema, y esto
     * NO es un detalle cosmetico: si devolviera siempre el mismo instante, todos
     * los avisos de un test se guardarian con la misma marca de tiempo y el
     * buzon — ordenado por fecha descendente — dejaria de tener un orden
     * definido. Solo se congela el tiempo en los tests que lo necesitan.
     */
    static class MutableClock extends Clock {

        private Instant pinned;

        void reset() {
            this.pinned = null;
        }

        void setTo(Instant moment) {
            this.pinned = moment;
        }

        @Override
        public Instant instant() {
            return pinned != null ? pinned : Instant.now();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    /**
     * Doble de prueba de {@link ClientPetsPort}, por el mismo motivo que
     * {@link FakeSitterCatalog}: la propiedad de las mascotas la resuelve
     * identity-service por HTTP y este test corre en solitario.
     */
    static class FakeClientPets implements ClientPetsPort {

        private final Map<UUID, Set<UUID>> petsByOwner = new HashMap<>();

        void register(UUID ownerId, UUID... petIds) {
            petsByOwner.put(ownerId, Set.of(petIds));
        }

        @Override
        public Set<UUID> petsOwnedBy(UUID ownerId) {
            return petsByOwner.getOrDefault(ownerId, Set.of());
        }

        @Override
        public List<PetDetails> detailsOf(java.util.Collection<UUID> petIds) {
            return petIds.stream()
                    .map(id -> new PetDetails(id, "Toby", "DOG", "Beagle", 3,
                            new java.math.BigDecimal("12.50"), null, null, "SOCIABLE", false))
                    .toList();
        }
    }

    /**
     * Doble de prueba de {@link SitterCatalogPort}: reemplaza la llamada HTTP real
     * a identity-service por un mapa en memoria que el propio test rellena.
     */
    static class FakeSitterCatalog implements SitterCatalogPort {

        private final Map<UUID, SitterSummary> sitters = new HashMap<>();

        void register(UUID sitterId, String firstName, boolean verified, String pricePerNight, int maxPets) {
            sitters.put(sitterId, new SitterSummary(sitterId, firstName, verified,
                    Map.of(ServiceType.NIGHT_ACCOMMODATION, Money.eur(pricePerNight)), maxPets,
                    "Calle Falsa 123, Springfield", 40.0, -3.0));
        }

        @Override
        public Optional<SitterSummary> findSitter(UUID sitterId) {
            return Optional.ofNullable(sitters.get(sitterId));
        }
    }
}
