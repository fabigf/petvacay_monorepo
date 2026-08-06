package com.petvacay.search.ports.in;

import java.util.List;
import java.util.Optional;

/**
 * Traduce una direccion o codigo postal escrito por el usuario a coordenadas,
 * para poder centrar la busqueda por radio ahi en vez de en un punto fijo.
 */
public interface GeocodeAddressUseCase {

    Optional<GeocodeResult> geocode(String query);

    /**
     * Candidatos para un texto a medio escribir, para el autocompletado del
     * formulario de perfil del cuidador: ahi la direccion no es un filtro que se
     * pueda afinar despues, es donde va a vivir un animal, y hay que poder
     * elegir la correcta entre las que se parecen.
     */
    List<GeocodeResult> suggest(String query);

    /** Tope de sugerencias: mas de cinco no caben en un desplegable util. */
    int SUGGESTION_LIMIT = 5;

    /** Longitud minima para consultar: con menos, todo se parece a todo. */
    int MIN_QUERY_LENGTH = 3;

    /** @param houseNumber ver {@code GeocodingPort.GeocodeResult}. */
    record GeocodeResult(double lat, double lng, String displayName, String houseNumber) {
    }
}
