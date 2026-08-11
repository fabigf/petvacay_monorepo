package com.petvacay.search.adapters.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fija que el buscador rechaza los parametros imposibles en su propia frontera.
 *
 * <p>Es el endpoint mas expuesto de la plataforma: publico, sin sesion y con
 * parametros numericos libres. Antes de estas validaciones, un
 * {@code radiusKm=-500} viajaba intacto hasta MongoDB, que respondia
 * {@code $maxDistance must be non-negative}, y el visitante recibia un 500 — un
 * error del cliente presentado como una averia del servidor, que ademas invita a
 * reintentar algo que no va a funcionar nunca.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DisplayName("search-service: validacion de los parametros de busqueda")
class SearchControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("un radio negativo se rechaza con 400, no revienta contra la base de datos")
    void negativeRadiusIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/search/sitters")
                        .param("lat", "40.4").param("lng", "-3.7").param("radiusKm", "-500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_INVALID_PARAMETER"))
                .andExpect(jsonPath("$.invalid_params[0].name").value("radiusKm"));
    }

    @Test
    @DisplayName("un radio desmesurado tambien se acota")
    void hugeRadiusIsRejected() throws Exception {
        // Sin tope, la "busqueda por cercania" devuelve el catalogo entero y
        // ordenarlo por distancia deja de significar nada.
        mockMvc.perform(get("/api/v1/search/sitters")
                        .param("lat", "40.4").param("lng", "-3.7").param("radiusKm", "999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("unas coordenadas fuera del planeta se rechazan")
    void impossibleCoordinatesAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/search/sitters").param("lat", "999").param("lng", "-3.7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_INVALID_PARAMETER"));

        mockMvc.perform(get("/api/v1/search/sitters").param("lat", "40.4").param("lng", "999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un numero de mascotas negativo o desmesurado se rechaza")
    void invalidPetCountIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/search/sitters")
                        .param("lat", "40.4").param("lng", "-3.7").param("petCount", "-5"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/search/sitters")
                        .param("lat", "40.4").param("lng", "-3.7").param("petCount", "999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("faltar un parametro obligatorio es 400, no 500")
    void missingRequiredParameterIsClientError() throws Exception {
        mockMvc.perform(get("/api/v1/search/sitters"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_MISSING_PARAMETER"));
    }

    @Test
    @DisplayName("una busqueda de geocodificacion vacia se rechaza")
    void blankGeocodeQueryIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/search/geocode").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("una busqueda con parametros validos sigue funcionando")
    void validSearchStillWorks() throws Exception {
        // La comprobacion importante de este test es que las validaciones nuevas
        // no han cerrado el camino normal.
        mockMvc.perform(get("/api/v1/search/sitters")
                        .param("lat", "40.4").param("lng", "-3.7").param("radiusKm", "25"))
                .andExpect(status().isOk());
    }
}
