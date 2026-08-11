package com.petvacay.search.adapters.in.web;

import com.petvacay.identity.domain.model.Species;
import com.petvacay.search.ports.in.GeocodeAddressUseCase;
import com.petvacay.search.ports.in.SearchSittersUseCase;
import com.petvacay.search.ports.in.SearchSittersUseCase.SitterSearchResult;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Lado de lectura del CQRS expuesto al modulo `search` de React
 * (especificaciones.md seccion 1.1).
 *
 * <p>{@code @Validated} activa la comprobacion de los parametros de query. Sin
 * ella, un radio negativo llegaba intacto hasta MongoDB, que lo rechazaba con
 * {@code $maxDistance must be non-negative} y el usuario recibia un 500 — un
 * error del cliente disfrazado de averia del servidor. Es un endpoint publico y
 * sin sesion, asi que es el que mas expuesto esta a recibir cualquier cosa.
 */
@RestController
@Validated
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchSittersUseCase searchSitters;
    private final GeocodeAddressUseCase geocodeAddress;

    public SearchController(SearchSittersUseCase searchSitters, GeocodeAddressUseCase geocodeAddress) {
        this.searchSitters = searchSitters;
        this.geocodeAddress = geocodeAddress;
    }

    /**
     * El radio se acota por arriba ademas de por abajo: una busqueda de radio
     * ilimitado devolveria el catalogo entero y ordenarlo por distancia no le
     * sirve de nada a quien busca a alguien cerca de su casa.
     */
    @GetMapping("/sitters")
    public List<SitterSearchResult> searchSitters(
            @RequestParam @DecimalMin(value = "-90", message = "La latitud está fuera de rango")
            @DecimalMax(value = "90", message = "La latitud está fuera de rango") double lat,
            @RequestParam @DecimalMin(value = "-180", message = "La longitud está fuera de rango")
            @DecimalMax(value = "180", message = "La longitud está fuera de rango") double lng,
            @RequestParam(required = false)
            @DecimalMin(value = "0.1", message = "El radio debe ser mayor que cero")
            @DecimalMax(value = "500", message = "El radio máximo de búsqueda es de 500 km") Double radiusKm,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Species petType,
            @RequestParam(required = false)
            @Positive(message = "El número de mascotas debe ser mayor que cero")
            @Max(value = 20, message = "El número de mascotas es demasiado alto") Integer petCount) {

        return searchSitters.search(new SearchSittersUseCase.SearchQuery(
                lat, lng, radiusKm, startDate, endDate, petType, petCount));
    }

    /**
     * Traduce una direccion o codigo postal a coordenadas, para centrar la
     * busqueda por radio ahi en vez de en un punto fijo (ver
     * NominatimGeocodingAdapter). Publico, como `/sitters`: no hace falta
     * sesion para buscar donde vas a estar de vacaciones.
     */
    @GetMapping("/geocode")
    public ResponseEntity<GeocodeAddressUseCase.GeocodeResult> geocode(
            @RequestParam @NotBlank(message = "Indica qué dirección buscar") String q) {
        return geocodeAddress.geocode(q)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Candidatos para un texto a medio escribir: alimenta el autocompletado de
     * direcciones del formulario de perfil del cuidador.
     *
     * <p>Devuelve una lista vacia — no un 404 — cuando no hay coincidencias: un
     * autocompletado se consulta constantemente mientras se teclea y "todavia no
     * hay nada que sugerir" es el caso normal, no un error.
     */
    @GetMapping("/geocode/suggest")
    public List<GeocodeAddressUseCase.GeocodeResult> suggest(@RequestParam String q) {
        return geocodeAddress.suggest(q);
    }
}
