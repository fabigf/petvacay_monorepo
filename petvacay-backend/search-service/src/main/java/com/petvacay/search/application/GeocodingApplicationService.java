package com.petvacay.search.application;

import com.petvacay.search.ports.in.GeocodeAddressUseCase;
import com.petvacay.search.ports.out.GeocodingPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GeocodingApplicationService implements GeocodeAddressUseCase {

    private final GeocodingPort geocoding;

    public GeocodingApplicationService(GeocodingPort geocoding) {
        this.geocoding = geocoding;
    }

    @Override
    public Optional<GeocodeResult> geocode(String query) {
        return geocoding.geocode(query)
                .map(result -> new GeocodeResult(result.lat(), result.lng(), result.displayName(),
                        result.houseNumber()));
    }

    /**
     * Con un texto demasiado corto no se consulta siquiera al proveedor: sus
     * resultados no servirian de nada y Nominatim limita a una peticion por
     * segundo EN TOTAL, asi que gastar cuota en teclear "Ca" perjudica a todos
     * los demas usuarios de la plataforma.
     */
    @Override
    public List<GeocodeResult> suggest(String query) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }

        return geocoding.suggest(query.trim(), SUGGESTION_LIMIT).stream()
                .map(result -> new GeocodeResult(result.lat(), result.lng(), result.displayName(),
                        result.houseNumber()))
                .toList();
    }
}
