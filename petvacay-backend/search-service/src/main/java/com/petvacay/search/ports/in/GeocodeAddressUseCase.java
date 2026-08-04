package com.petvacay.search.ports.in;

import java.util.Optional;

/**
 * Traduce una direccion o codigo postal escrito por el usuario a coordenadas,
 * para poder centrar la busqueda por radio ahi en vez de en un punto fijo.
 */
public interface GeocodeAddressUseCase {

    Optional<GeocodeResult> geocode(String query);

    record GeocodeResult(double lat, double lng, String displayName) {
    }
}
