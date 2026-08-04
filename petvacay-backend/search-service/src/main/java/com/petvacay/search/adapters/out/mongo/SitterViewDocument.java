package com.petvacay.search.adapters.out.mongo;

import com.petvacay.identity.domain.model.HomeType;
import com.petvacay.identity.domain.model.Species;
import com.petvacay.search.domain.model.SitterView;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Documento del modelo de lectura del buscador (Fase 2).
 *
 * <p>Es una vista plana pensada para responder la busqueda con una sola consulta:
 * la ubicacion se guarda como GeoJSON con indice 2dsphere y la ocupacion por noche
 * viaja embebida en el propio documento, evitando cualquier join.
 */
@Document(collection = "sitter_views")
public class SitterViewDocument {

    @Id
    private UUID sitterId;

    private String firstName;
    private BigDecimal rating;
    private int reviewCount;
    private BigDecimal pricePerNight;
    private String currency;
    private HomeType homeType;

    /** GeoJSON en orden [longitud, latitud], como exige MongoDB. */
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint location;

    private int maxSimultaneousPets;
    private Set<Species> acceptedSpecies = new HashSet<>();
    private BigDecimal maxPetWeightKg;

    /**
     * Mascotas comprometidas por noche. Las claves de un mapa de Mongo deben ser
     * cadenas, asi que se usa la fecha en formato ISO-8601.
     */
    @Field("occupancy")
    private Map<String, Integer> occupancyByDate = new HashMap<>();

    protected SitterViewDocument() {
    }

    public static SitterViewDocument fromDomain(SitterView view) {
        SitterViewDocument document = new SitterViewDocument();
        document.applyCatalogData(view);
        document.occupancyByDate = new HashMap<>();
        view.occupancyByDate().forEach((date, count) ->
                document.occupancyByDate.put(date.toString(), count));
        return document;
    }

    /** Actualiza los datos de catalogo conservando la ocupacion ya registrada. */
    public void applyCatalogData(SitterView view) {
        this.sitterId = view.sitterId();
        this.firstName = view.firstName();
        this.rating = view.rating();
        this.reviewCount = view.reviewCount();
        this.pricePerNight = view.pricePerNight();
        this.currency = view.currency();
        this.homeType = view.homeType();
        this.location = new GeoJsonPoint(view.longitude(), view.latitude());
        this.maxSimultaneousPets = view.maxSimultaneousPets();
        this.acceptedSpecies = new HashSet<>(view.acceptedSpecies());
        this.maxPetWeightKg = view.maxPetWeightKg();
    }

    public SitterView toDomain() {
        Map<LocalDate, Integer> occupancy = new HashMap<>();
        occupancyByDate.forEach((date, count) -> {
            // Las liberaciones dejan claves a cero; no son ocupacion real.
            if (count != null && count > 0) {
                occupancy.put(LocalDate.parse(date), count);
            }
        });

        return new SitterView(
                sitterId,
                firstName,
                rating,
                reviewCount,
                pricePerNight,
                currency,
                homeType,
                location.getY(),
                location.getX(),
                maxSimultaneousPets,
                acceptedSpecies.isEmpty() ? EnumSet.noneOf(Species.class) : EnumSet.copyOf(acceptedSpecies),
                maxPetWeightKg,
                occupancy);
    }

    public UUID getSitterId() {
        return sitterId;
    }
}
