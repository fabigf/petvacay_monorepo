package com.petvacay.search.application;

import com.petvacay.search.ports.in.GeocodeAddressUseCase.GeocodeResult;
import com.petvacay.search.ports.out.GeocodingPort;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unitario y sin contexto de Spring, igual que los tests de dominio del resto
 * del proyecto: lo unico que hay que probar aqui es la traduccion entre el
 * resultado del puerto de salida y el del caso de uso, no infraestructura.
 */
class GeocodingApplicationServiceTest {

    @Test
    void translatesPortResultToUseCaseResult() {
        GeocodingPort fakePort = query -> Optional.of(
                new GeocodingPort.GeocodeResult(40.4168, -3.7038, "Madrid, España"));
        GeocodingApplicationService service = new GeocodingApplicationService(fakePort);

        Optional<GeocodeResult> result = service.geocode("Madrid");

        assertThat(result).contains(new GeocodeResult(40.4168, -3.7038, "Madrid, España"));
    }

    @Test
    void propagatesEmptyWhenAddressIsNotFound() {
        GeocodingPort fakePort = query -> Optional.empty();
        GeocodingApplicationService service = new GeocodingApplicationService(fakePort);

        Optional<GeocodeResult> result = service.geocode("esto no existe en ningun sitio");

        assertThat(result).isEmpty();
    }
}
