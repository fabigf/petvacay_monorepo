package com.petvacay.search.application;

import com.petvacay.search.ports.in.GeocodeAddressUseCase;
import com.petvacay.search.ports.out.GeocodingPort;
import org.springframework.stereotype.Service;

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
                .map(result -> new GeocodeResult(result.lat(), result.lng(), result.displayName()));
    }
}
