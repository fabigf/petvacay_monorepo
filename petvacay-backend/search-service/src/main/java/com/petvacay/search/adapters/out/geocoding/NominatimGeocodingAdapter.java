package com.petvacay.search.adapters.out.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.petvacay.search.ports.out.GeocodingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Geocodificacion via Nominatim (nominatim.openstreetmap.org), el servicio
 * publico y gratuito de OpenStreetMap. Se eligio sobre Google/Mapbox porque no
 * exige alta ni tarjeta de credito para levantar el entorno: encaja con el
 * mismo criterio que el adaptador `simulated` de Stripe en payment-service —
 * nadie deberia necesitar una cuenta de pago para poder arrancar el proyecto.
 *
 * <p><b>Limite real a tener en cuenta antes de tráfico serio:</b> la politica
 * de uso de Nominatim exige como maximo 1 peticion/segundo EN TOTAL (no por
 * cliente) y prohibe consultas automatizadas en bucle. El rate limiting del
 * Gateway (ver {@code gateway-service/application.yml}, ruta
 * {@code search-geocode}) limita por IP, asi que con muchos usuarios
 * concurrentes el conjunto podria seguir superando ese limite global — para
 * produccion con trafico real, sustituir esta clase por un proveedor de pago
 * (Google/Mapbox) o una instancia propia de Nominatim autoalojada, ambos sin
 * ese techo. Cambiar de proveedor no toca nada fuera de este fichero: el resto
 * del sistema solo conoce {@link GeocodingPort}.
 */
@Component
public class NominatimGeocodingAdapter implements GeocodingPort {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodingAdapter.class);

    private final RestClient restClient;

    public NominatimGeocodingAdapter(
            @Value("${petvacay.geocoding.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
            // Nominatim EXIGE un User-Agent que identifique la aplicacion (rechaza
            // el User-Agent generico por defecto de los clientes HTTP). El valor de
            // ejemplo sirve para desarrollo; en produccion debe apuntar a un
            // contacto real, tal como pide su politica de uso.
            @Value("${petvacay.geocoding.user-agent:PetVacay/1.0 (contacto@petvacay.test)}") String userAgent) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(3).toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", userAgent)
                .build();
    }

    @Override
    public Optional<GeocodeResult> geocode(String query) {
        try {
            List<NominatimResult> results = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("format", "json")
                            .queryParam("q", query)
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NominatimResult>>() {
                    });

            if (results == null || results.isEmpty()) {
                return Optional.empty();
            }

            NominatimResult first = results.get(0);
            return Optional.of(new GeocodeResult(
                    Double.parseDouble(first.lat()), Double.parseDouble(first.lon()), first.displayName()));
        } catch (RestClientException | NumberFormatException e) {
            // Nunca se propaga: un proveedor externo caido no debe tumbar la
            // busqueda ni devolver un 500 al usuario. El controlador lo trata
            // igual que "direccion no encontrada".
            log.warn("Fallo geocodificando '{}' contra Nominatim: {}", query, e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimResult(String lat, String lon, @JsonProperty("display_name") String displayName) {
    }
}
