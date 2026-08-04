package com.petvacay.search.ports.out;

import java.util.Optional;

/**
 * SPI hacia un proveedor externo de geocodificacion (texto libre -> coordenadas).
 *
 * <p>Separado de {@link SitterReadModelPort} a proposito: uno lee el modelo de
 * lectura propio en MongoDB, el otro habla con un servicio de terceros. Mezclar
 * ambos en el mismo puerto acoplaria la disponibilidad del buscador (dato propio,
 * siempre disponible) a la de un proveedor externo (puede caerse, tener cuota, o
 * cambiarse por otro sin que el resto del dominio se entere).
 */
public interface GeocodingPort {

    Optional<GeocodeResult> geocode(String query);

    record GeocodeResult(double lat, double lng, String displayName) {
    }
}
