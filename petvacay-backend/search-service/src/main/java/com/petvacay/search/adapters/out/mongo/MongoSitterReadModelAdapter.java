package com.petvacay.search.adapters.out.mongo;

import com.petvacay.search.domain.model.SitterView;
import com.petvacay.search.ports.out.SitterReadModelPort;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Modelo de lectura del buscador sobre MongoDB.
 *
 * <p>Es la unica implementacion de {@link SitterReadModelPort} desde la Fase 4:
 * el acotado por radio lo resuelve el indice 2dsphere en lugar de recorrer una
 * tabla en memoria, y los bloqueos de calendario se aplican con operadores
 * atomicos {@code $inc} en vez de leer, modificar y reescribir el documento, algo
 * imprescindible con los eventos llegando por Kafka y procesandose en paralelo.
 */
@Component
public class MongoSitterReadModelAdapter implements SitterReadModelPort {

    private static final Logger log = LoggerFactory.getLogger(MongoSitterReadModelAdapter.class);

    private final SitterViewMongoRepository repository;
    private final MongoTemplate mongoTemplate;

    public MongoSitterReadModelAdapter(SitterViewMongoRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Crea el indice 2dsphere que exige {@code $nearSphere}.
     *
     * <p>Se hace aqui en vez de con {@code spring.data.mongodb.auto-index-creation}
     * porque esa propiedad obliga a MongoTemplate a conectarse durante el arranque
     * del contexto, lo que impediria levantar el perfil de desarrollo (que usa el
     * modelo de lectura en PostgreSQL) sin tener un Mongo escuchando. Al colgarlo de
     * este bean, el indice se crea exactamente cuando el almacen Mongo esta activo.
     */
    @PostConstruct
    void ensureGeoIndex() {
        mongoTemplate.indexOps(SitterViewDocument.class)
                .ensureIndex(new GeospatialIndex("location").typed(GeoSpatialIndexType.GEO_2DSPHERE));
        log.info("Modelo de lectura del buscador servido desde MongoDB (indice 2dsphere listo)");
        migrateStringRatingSums();
    }

    /**
     * Convierte a numero los {@code ratingSum} que quedaron guardados como cadena.
     *
     * <p>Cuando el campo era {@code BigDecimal}, Spring Data lo serializaba como
     * cadena y {@code $inc} fallaba en cada resena. Los documentos escritos antes
     * del arreglo conservan ese tipo, y {@code $inc} seguiria fallando sobre
     * ellos aunque el codigo ya sea correcto: hay que arreglar tambien el dato.
     *
     * <p>Va aqui y no en un script aparte porque una proyeccion debe poder
     * levantarse sola: quien despliegue esta version no tiene por que acordarse
     * de ejecutar nada a mano contra Mongo. Es idempotente — al segundo arranque
     * el filtro por tipo ya no encuentra nada.
     */
    private void migrateStringRatingSums() {
        long migrated = mongoTemplate.getCollection(
                        mongoTemplate.getCollectionName(SitterViewDocument.class))
                .updateMany(
                        new org.bson.Document("ratingSum", new org.bson.Document("$type", "string")),
                        List.of(new org.bson.Document("$set", new org.bson.Document("ratingSum",
                                new org.bson.Document("$toLong",
                                        new org.bson.Document("$toDouble", "$ratingSum"))))))
                .getModifiedCount();

        if (migrated > 0) {
            log.info("{} documentos del buscador migrados: ratingSum pasa de cadena a numero", migrated);
        }
    }

    @Override
    public void upsert(SitterView view) {
        SitterViewDocument document = repository.findById(view.sitterId())
                .orElseGet(() -> SitterViewDocument.fromDomain(view));
        document.applyCatalogData(view);
        repository.save(document);
    }

    @Override
    public void remove(UUID sitterId) {
        repository.deleteById(sitterId);
    }

    @Override
    public void removeAll() {
        repository.deleteAll();
    }

    @Override
    public Optional<SitterView> findById(UUID sitterId) {
        return repository.findById(sitterId).map(SitterViewDocument::toDomain);
    }

    @Override
    public List<SitterView> findNearby(double lat, double lng, double radiusKm) {
        // GeoJSON usa el orden [longitud, latitud]
        Query query = Query.query(Criteria.where("location")
                .nearSphere(new Point(lng, lat))
                .maxDistance(new Distance(radiusKm, Metrics.KILOMETERS).getNormalizedValue()));

        return mongoTemplate.find(query, SitterViewDocument.class).stream()
                .map(SitterViewDocument::toDomain)
                .toList();
    }

    @Override
    public void blockDates(UUID sitterId, LocalDate start, LocalDate end, int petCount) {
        applyOccupancyDelta(sitterId, start, end, petCount);
    }

    @Override
    public void releaseDates(UUID sitterId, LocalDate start, LocalDate end, int petCount) {
        applyOccupancyDelta(sitterId, start, end, -petCount);
    }

    @Override
    public void recordReview(UUID sitterId, int rating) {
        Update update = new Update()
                .inc("ratingSum", rating)
                .inc("reviewCount", 1);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(sitterId)),
                update, SitterViewDocument.class);
    }

    @Override
    public void setReputation(UUID sitterId, long ratingSum, int reviewCount) {
        Update update = new Update()
                .set("ratingSum", ratingSum)
                .set("reviewCount", reviewCount);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(sitterId)),
                update, SitterViewDocument.class);
    }

    @Override
    public void recordSitterCancellation(UUID sitterId) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(sitterId)),
                new Update().inc("cancellationCount", 1), SitterViewDocument.class);
    }

    /** Un unico {@code $inc} por noche, atomico respecto a otros consumidores. */
    private void applyOccupancyDelta(UUID sitterId, LocalDate start, LocalDate end, int delta) {
        if (delta == 0) {
            return;
        }

        Update update = new Update();
        for (LocalDate night = start; night.isBefore(end); night = night.plusDays(1)) {
            update.inc("occupancy." + night, delta);
        }

        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(sitterId)),
                update, SitterViewDocument.class);
    }
}
