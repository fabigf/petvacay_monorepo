package com.petvacay.search.ports.out;

import java.util.List;
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

    /**
     * Candidatos para un texto incompleto, ordenados por relevancia.
     *
     * <p>Distinto de {@link #geocode}: aquel resuelve una direccion que ya se da
     * por buena y devuelve la mejor coincidencia; este alimenta un autocompletado
     * mientras se escribe, donde lo util es poder elegir entre varias — "Calle
     * Mayor" existe en decenas de pueblos y quedarse con la primera acertaria
     * pocas veces.
     */
    List<GeocodeResult> suggest(String query, int limit);

    /**
     * @param houseNumber numero de portal, cuando el resultado llega a ese nivel
     *                    de detalle; {@code null} si Nominatim solo pudo situar
     *                    la calle (el numero pedido no esta mapeado, o no se
     *                    pidio ninguno). Es la unica forma fiable de saber si una
     *                    direccion quedo geocodificada a nivel de portal o de
     *                    calle — comparar contra el {@code displayName} en texto
     *                    libre es fragil.
     */
    record GeocodeResult(double lat, double lng, String displayName, String houseNumber) {
    }
}
