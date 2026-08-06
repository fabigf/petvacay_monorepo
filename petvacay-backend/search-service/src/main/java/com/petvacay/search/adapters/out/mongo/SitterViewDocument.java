package com.petvacay.search.adapters.out.mongo;

import com.petvacay.identity.domain.model.HomeType;
import com.petvacay.identity.domain.model.ServiceType;
import com.petvacay.identity.domain.model.Species;
import com.petvacay.search.domain.model.SitterView;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /**
     * Suma de todas las puntuaciones recibidas; el promedio se calcula al leer.
     *
     * <p>Es {@code long} y no {@code BigDecimal} por una razon muy concreta:
     * Spring Data MongoDB serializa {@code BigDecimal} como **cadena**, y
     * {@code $inc} no puede aplicarse a una cadena — el operador fallaba en cada
     * resena con "Cannot apply $inc to a value of non-numeric type", de modo que
     * el rating del buscador nunca llegaba a subir. Una suma de puntuaciones
     * enteras (1-5) es entera por definicion; el unico decimal del asunto es el
     * promedio, que se calcula al leer y sigue siendo {@code BigDecimal}.
     */
    private long ratingSum;

    private int reviewCount;
    private BigDecimal pricePerNight;
    private String currency;
    private HomeType homeType;

    /** GeoJSON en orden [longitud, latitud], como exige MongoDB. */
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint location;

    private int maxSimultaneousPets;
    private Set<Species> acceptedSpecies = new HashSet<>();
    private Set<ServiceType> offeredServices = new HashSet<>();
    private BigDecimal maxPetWeightKg;
    private String photoUrl;

    /**
     * Reservas confirmadas canceladas por el cuidador. Igual que {@code ratingSum},
     * solo lo muta un {@code $inc} atomico y una reindexacion del catalogo no lo
     * borra: es historial, no configuracion del perfil.
     */
    private int cancellationCount;

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
        // Documento nuevo: sin resenas todavia, sea lo que sea lo que traiga el
        // evento de catalogo (el handler siempre manda ZERO/0 aqui de todos
        // modos — ver SitterProjectionHandler.onCuidadorIndexado).
        document.ratingSum = 0;
        document.reviewCount = 0;
        document.cancellationCount = 0;
        document.occupancyByDate = new HashMap<>();
        view.occupancyByDate().forEach((date, count) ->
                document.occupancyByDate.put(date.toString(), count));
        return document;
    }

    /**
     * Actualiza los datos de catalogo conservando la ocupacion ya registrada.
     *
     * <p>Deliberadamente NO toca {@code ratingSum} ni {@code reviewCount}: esos
     * campos solo los muta {@code recordReview} con {@code $inc} atomico. Una
     * reindexacion del catalogo (cambio de tarifa, descripcion...) no debe poder
     * borrar el historial de resenas acumulado.
     */
    public void applyCatalogData(SitterView view) {
        this.sitterId = view.sitterId();
        this.firstName = view.firstName();
        this.pricePerNight = view.pricePerNight();
        this.currency = view.currency();
        this.homeType = view.homeType();
        this.location = new GeoJsonPoint(view.longitude(), view.latitude());
        this.maxSimultaneousPets = view.maxSimultaneousPets();
        this.acceptedSpecies = new HashSet<>(view.acceptedSpecies());
        this.offeredServices = new HashSet<>(view.offeredServices());
        this.maxPetWeightKg = view.maxPetWeightKg();
        this.photoUrl = view.photoUrl();
    }

    public SitterView toDomain() {
        Map<LocalDate, Integer> occupancy = new HashMap<>();
        occupancyByDate.forEach((date, count) -> {
            // Las liberaciones dejan claves a cero; no son ocupacion real.
            if (count != null && count > 0) {
                occupancy.put(LocalDate.parse(date), count);
            }
        });

        BigDecimal rating = reviewCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(ratingSum)
                        .divide(BigDecimal.valueOf(reviewCount), 1, RoundingMode.HALF_UP);

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
                offeredServices.isEmpty() ? EnumSet.noneOf(ServiceType.class) : EnumSet.copyOf(offeredServices),
                maxPetWeightKg,
                photoUrl,
                occupancy,
                cancellationCount);
    }

    public UUID getSitterId() {
        return sitterId;
    }
}
