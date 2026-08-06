package com.petvacay.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petvacay.identity.ports.in.IdentityUseCases.RegisterUserUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fija quien puede ver y hacer que en identity-service.
 *
 * <p>Cubre tres garantias que el codigo promete por escrito pero que nada
 * verificaba, y que un cambio descuidado podria romper sin que ningun otro test
 * fallara:
 *
 * <ul>
 *   <li>La direccion exacta de un cuidador no sale nunca por la vista publica de
 *       su perfil. Es el dato mas sensible del servicio y la promesa esta escrita
 *       en el javadoc de {@code SitterProfileResponse}: una sola sobrecarga mal
 *       elegida ({@code from(profile)} en vez de {@code from(profile, nombre)})
 *       la convierte en publica sin romper nada visible.</li>
 *   <li>El registro publico no concede {@code ROLE_ADMIN}. Si lo hiciera,
 *       cualquiera podria aprobar su propio KYC.</li>
 *   <li>Las mascotas y el perfil son de su dueno: ni se listan ni se borran las
 *       de otro.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "petvacay.seed.enabled=false",
        "petvacay.admin.bootstrap.enabled=false",
})
@DisplayName("identity-service: autorizacion y privacidad de datos")
class IdentityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RegisterUserUseCase registerUser;

    private UUID sitterId;

    private static final String ADDRESS = "Calle de Alcalá";
    private static final String STREET_NUMBER = "45";
    private static final String FLOOR_DOOR = "3ºA";

    @BeforeEach
    void setUp() throws Exception {
        sitterId = registerSitter();
        mockMvc.perform(post("/api/v1/sitters/profile")
                        .with(asSitter())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload()))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------- privacidad de la direccion

    @Test
    @DisplayName("la vista publica de un perfil no expone direccion, portal ni planta")
    void publicProfileHidesTheExactAddress() throws Exception {
        mockMvc.perform(get("/api/v1/sitters/" + sitterId + "/profile").with(asStranger()))
                .andExpect(status().isOk())
                // Lo que si lleva: es lo que necesita la ficha previa a reservar.
                .andExpect(jsonPath("$.firstName").value("Lucia"))
                .andExpect(jsonPath("$.homeType").value("APARTMENT"))
                // Y lo que no puede llevar bajo ningun concepto.
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.streetNumber").doesNotExist())
                .andExpect(jsonPath("$.floorDoor").doesNotExist())
                .andExpect(jsonPath("$.fullAddress").doesNotExist());
    }

    /**
     * Comprobacion en crudo sobre el JSON, no solo por campo: si algun dia se
     * añade otro campo con la calle dentro (un "resumen", un texto de
     * presentacion autogenerado), los {@code doesNotExist} de arriba seguirian
     * pasando mientras la direccion viaja igualmente.
     */
    @Test
    @DisplayName("la calle no aparece en ninguna parte del JSON publico")
    void publicProfileBodyDoesNotContainTheStreetAnywhere() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sitters/" + sitterId + "/profile").with(asStranger()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(ADDRESS)
                .doesNotContain(FLOOR_DOOR);
    }

    @Test
    @DisplayName("el cuidador si ve su propia direccion completa, para poder corregirla")
    void ownProfileIncludesTheAddress() throws Exception {
        mockMvc.perform(get("/api/v1/sitters/profile").with(asSitter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(ADDRESS))
                .andExpect(jsonPath("$.streetNumber").value(STREET_NUMBER))
                .andExpect(jsonPath("$.floorDoor").value(FLOOR_DOOR));
    }

    @Test
    @DisplayName("el perfil propio devuelve siempre el del autenticado, nunca el de otro")
    void ownProfileRouteIsAlwaysScopedToTheCaller() throws Exception {
        UUID otherSitter = registerSitter();

        // Un cuidador distinto pregunta por "su" perfil: como no ha creado
        // ninguno, obtiene un 404 — jamas el del cuidador de al lado.
        mockMvc.perform(get("/api/v1/sitters/profile").with(tokenFor(otherSitter, "ROLE_SITTER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un cliente sin rol de cuidador no alcanza la ruta del perfil propio")
    void ownProfileRouteRequiresSitterRole() throws Exception {
        mockMvc.perform(get("/api/v1/sitters/profile").with(asStranger()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sin token no se puede leer ningun perfil")
    void anonymousCannotReadProfiles() throws Exception {
        mockMvc.perform(get("/api/v1/sitters/" + sitterId + "/profile"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------ escalada de privilegios

    @Test
    @DisplayName("el registro publico nunca concede rol de administrador")
    void publicRegistrationCannotGrantAdmin() throws Exception {
        String payload = """
                {
                  "email": "%s",
                  "password": "password123",
                  "firstName": "Mario",
                  "lastName": "Ruiz",
                  "birthDate": "1990-05-05",
                  "asSitter": true,
                  "roles": ["ROLE_ADMIN"]
                }
                """.formatted(uniqueEmail());

        MvcResult created = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode roles = objectMapper.readTree(created.getResponse().getContentAsString()).get("roles");
        assertThat(roles.toString()).doesNotContain("ROLE_ADMIN");
    }

    @Test
    @DisplayName("el back-office exige rol de administrador, no basta con estar autenticado")
    void adminRoutesRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").with(asStranger()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/sitters").with(asSitter()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/sitters/" + sitterId + "/approve").with(asSitter()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/users").with(asAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un cuidador no puede aprobarse a si mismo el KYC")
    void sitterCannotApproveOwnVerification() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sitters/" + sitterId + "/approve").with(asSitter()))
                .andExpect(status().isForbidden());

        // Y sigue sin ser visible en el buscador.
        mockMvc.perform(get("/api/v1/sitters/profile").with(asSitter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchable").value(false));
    }

    // ------------------------------------------------------- mascotas de cada dueno

    @Test
    @DisplayName("cada usuario solo ve las mascotas de su propio perfil")
    void petsAreScopedToTheirOwner() throws Exception {
        UUID owner = registerSitter();
        mockMvc.perform(post("/api/v1/pets")
                        .with(tokenFor(owner, "ROLE_CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(petPayload()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/pets").with(tokenFor(owner, "ROLE_CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Otro usuario tiene su propio buzon de mascotas, vacio.
        mockMvc.perform(get("/api/v1/pets").with(asStranger()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("no se puede borrar la mascota de otra persona aunque se sepa su id")
    void cannotDeleteSomeoneElsesPet() throws Exception {
        UUID owner = registerSitter();
        MvcResult created = mockMvc.perform(post("/api/v1/pets")
                        .with(tokenFor(owner, "ROLE_CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(petPayload()))
                .andExpect(status().isCreated())
                .andReturn();

        String petId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("petId").asText();

        mockMvc.perform(delete("/api/v1/pets/" + petId).with(asStranger()))
                .andExpect(status().is4xxClientError());

        // Sigue estando ahi para su dueno.
        mockMvc.perform(get("/api/v1/pets").with(tokenFor(owner, "ROLE_CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("/users/me devuelve el usuario del token, no uno elegido por el cliente")
    void meIsResolvedFromTheToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(asSitter())
                        .header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(sitterId.toString()));
    }

    // --------------------------------------------------------------------- apoyo

    private UUID registerSitter() {
        return registerUser.register(new RegisterUserUseCase.RegisterUserCommand(
                uniqueEmail(), "password123", "Lucia", "Marin",
                LocalDate.of(1992, 3, 14), true)).id();
    }

    private static String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@petvacay.test";
    }

    private String profilePayload() {
        return """
                {
                  "description": "Piso con terraza y mucho cariño",
                  "homeType": "APARTMENT",
                  "latitude": 40.4168,
                  "longitude": -3.7038,
                  "address": "%s",
                  "streetNumber": "%s",
                  "floorDoor": "%s",
                  "servicePrices": { "NIGHT_ACCOMMODATION": 25.00 },
                  "maxSimultaneousPets": 2,
                  "acceptedSpecies": ["DOG"],
                  "maxPetWeightKg": 20.0
                }
                """.formatted(ADDRESS, STREET_NUMBER, FLOOR_DOOR);
    }

    private String petPayload() {
        return """
                {
                  "name": "Toby",
                  "species": "DOG",
                  "breed": "Beagle",
                  "ageYears": 3,
                  "weightKg": 12.50,
                  "sociability": "SOCIABLE"
                }
                """;
    }

    private JwtRequestPostProcessor asSitter() {
        return tokenFor(sitterId, "ROLE_SITTER");
    }

    /** Autenticado de verdad, pero sin relacion con el perfil bajo prueba. */
    private JwtRequestPostProcessor asStranger() {
        return tokenFor(UUID.randomUUID(), "ROLE_CLIENT");
    }

    private JwtRequestPostProcessor asAdmin() {
        return tokenFor(UUID.randomUUID(), "ROLE_ADMIN");
    }

    private JwtRequestPostProcessor tokenFor(UUID userId, String role) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
