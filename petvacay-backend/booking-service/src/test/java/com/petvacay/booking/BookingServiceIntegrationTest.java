package com.petvacay.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    private final UUID clientId = UUID.randomUUID();
    private final UUID petId = UUID.randomUUID();
    private UUID sitterId;

    private final LocalDate startDate = LocalDate.now().plusDays(30);
    private final LocalDate endDate = startDate.plusDays(7);

    @BeforeEach
    void setUp() {
        sitterId = UUID.randomUUID();
        sitterCatalog.register(sitterId, "Carlos", true, "25.00", 1);
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
    }

    /**
     * Doble de prueba de {@link SitterCatalogPort}: reemplaza la llamada HTTP real
     * a identity-service por un mapa en memoria que el propio test rellena.
     */
    static class FakeSitterCatalog implements SitterCatalogPort {

        private final Map<UUID, SitterSummary> sitters = new HashMap<>();

        void register(UUID sitterId, String firstName, boolean verified, String pricePerNight, int maxPets) {
            sitters.put(sitterId, new SitterSummary(sitterId, firstName, verified,
                    Money.eur(pricePerNight), maxPets));
        }

        @Override
        public Optional<SitterSummary> findSitter(UUID sitterId) {
            return Optional.ofNullable(sitters.get(sitterId));
        }
    }
}
