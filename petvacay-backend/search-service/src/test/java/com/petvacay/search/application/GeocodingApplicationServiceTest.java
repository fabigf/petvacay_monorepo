package com.petvacay.search.application;

import com.petvacay.search.ports.in.GeocodeAddressUseCase.GeocodeResult;
import com.petvacay.search.ports.out.GeocodingPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unitario y sin contexto de Spring, igual que los tests de dominio del resto
 * del proyecto: lo unico que hay que probar aqui es la traduccion entre el
 * resultado del puerto de salida y el del caso de uso, no infraestructura.
 */
class GeocodingApplicationServiceTest {

    private static final GeocodingPort.GeocodeResult MADRID =
            new GeocodingPort.GeocodeResult(40.4168, -3.7038, "Madrid, España", null);

    @Test
    void translatesPortResultToUseCaseResult() {
        GeocodingApplicationService service = new GeocodingApplicationService(new FakePort(MADRID));

        Optional<GeocodeResult> result = service.geocode("Madrid");

        assertThat(result).contains(new GeocodeResult(40.4168, -3.7038, "Madrid, España", null));
    }

    @Test
    void propagatesEmptyWhenAddressIsNotFound() {
        GeocodingApplicationService service = new GeocodingApplicationService(new FakePort());

        Optional<GeocodeResult> result = service.geocode("esto no existe en ningun sitio");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("el autocompletado devuelve todos los candidatos, no solo el primero")
    void suggestReturnsEveryCandidate() {
        GeocodingApplicationService service = new GeocodingApplicationService(new FakePort(
                MADRID, new GeocodingPort.GeocodeResult(41.3874, 2.1686, "Barcelona, España", null)));

        assertThat(service.suggest("Calle Mayor")).hasSize(2);
    }

    /**
     * Fija la regla que protege la cuota del proveedor: Nominatim admite una
     * peticion por segundo EN TOTAL, asi que un texto demasiado corto ni siquiera
     * llega a consultarse.
     */
    @Test
    @DisplayName("propaga el número de portal cuando el proveedor lo geocodifica a ese nivel")
    void propagatesHouseNumberWhenGeocodedAtThatLevel() {
        GeocodingPort.GeocodeResult withNumber =
                new GeocodingPort.GeocodeResult(40.4165, -3.7059, "10, Calle Mayor, Madrid", "10");
        GeocodingApplicationService service = new GeocodingApplicationService(new FakePort(withNumber));

        Optional<GeocodeResult> result = service.geocode("10 Calle Mayor, Madrid");

        assertThat(result).map(GeocodeResult::houseNumber).contains("10");
    }

    @Test
    @DisplayName("no consulta al proveedor con un texto demasiado corto")
    void doesNotCallTheProviderForVeryShortQueries() {
        FakePort port = new FakePort(MADRID);
        GeocodingApplicationService service = new GeocodingApplicationService(port);

        assertThat(service.suggest("Ca")).isEmpty();
        assertThat(port.suggestCalls).isZero();
    }

    /** Doble de prueba explicito: el puerto ya declara dos metodos y no es funcional. */
    private static final class FakePort implements GeocodingPort {

        private final List<GeocodeResult> results;
        private int suggestCalls;

        private FakePort(GeocodeResult... results) {
            this.results = List.of(results);
        }

        @Override
        public Optional<GeocodeResult> geocode(String query) {
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        }

        @Override
        public List<GeocodeResult> suggest(String query, int limit) {
            suggestCalls++;
            return results.stream().limit(limit).toList();
        }
    }
}
