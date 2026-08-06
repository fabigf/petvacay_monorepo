package com.petvacay.search.ports.out;

import com.petvacay.search.domain.model.SitterView;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SPI del modelo de lectura del buscador.
 *
 * <p>Su unica implementacion es MongoDB ({@code MongoSitterReadModelAdapter}).
 * Hasta la Fase 3 existia tambien una implementacion sobre PostgreSQL/H2, util
 * cuando este modulo formaba parte de un monolito que debia poder arrancar sin
 * infraestructura externa; se retiro al independizar search-service en la Fase 4
 * porque dejo de aportar nada (nadie mas comparte su JVM) y no tenia tests que la
 * ejercitaran. El puerto se mantiene como interfaz aunque solo tenga un
 * implementador: el servicio de aplicacion sigue sin conocer la tecnologia
 * concreta, y una eventual segunda implementacion (por ejemplo Elasticsearch para
 * busqueda por texto libre) seguiria encajando sin tocar
 * {@code SearchApplicationService} ni {@code SitterProjectionHandler}.
 */
public interface SitterReadModelPort {

    /** Alta o reproyeccion completa de la vista de un cuidador. */
    void upsert(SitterView view);

    /** Retira al cuidador del indice (KYC rechazado o cuenta suspendida). */
    void remove(UUID sitterId);

    /**
     * Vacia el indice completo.
     *
     * <p>Una proyeccion es estado derivado: siempre debe poder reconstruirse desde el
     * modelo de escritura. Hoy lo usa la carga de datos de desarrollo para no arrastrar
     * proyecciones de arranques anteriores, y sera la base del "replay" de eventos
     * cuando en Fase 3 haya que reconstruir el indice desde Kafka.
     */
    void removeAll();

    Optional<SitterView> findById(UUID sitterId);

    /**
     * Cuidadores indexados dentro del radio indicado.
     *
     * <p>El filtro geografico se delega al almacen para que cada tecnologia use lo
     * mejor que tenga: MongoDB resuelve con un indice 2dsphere, mientras que el
     * adaptador de PostgreSQL calcula la distancia en memoria. El resto de filtros
     * (especie, disponibilidad) los aplica el servicio de aplicacion.
     */
    List<SitterView> findNearby(double lat, double lng, double radiusKm);

    /** Suma mascotas ocupadas en cada noche del rango (reserva confirmada). */
    void blockDates(UUID sitterId, LocalDate start, LocalDate end, int petCount);

    /** Libera la ocupacion previamente bloqueada (cancelacion o finalizacion). */
    void releaseDates(UUID sitterId, LocalDate start, LocalDate end, int petCount);

    /**
     * Registra una nueva resena en el agregado del cuidador (suma y contador,
     * ambos con {@code $inc} atomico — mismo motivo que blockDates/releaseDates:
     * varias resenas pueden llegar por Kafka casi a la vez).
     */
    void recordReview(UUID sitterId, int rating);

    /**
     * Fija la reputacion completa de un cuidador con valores absolutos, en lugar
     * de sumar uno mas. Es la operacion con la que se reconstruye la proyeccion
     * cuando se ha desincronizado del modelo de escritura; al ser un {@code $set}
     * y no un {@code $inc}, repetirla no altera el resultado.
     */
    void setReputation(UUID sitterId, long ratingSum, int reviewCount);

    /**
     * Anota que el cuidador cancelo una reserva ya confirmada, lo que le penaliza
     * en el orden del listado (negocio.md 4.3). Tambien con {@code $inc} atomico,
     * por el mismo motivo que el resto de contadores.
     */
    void recordSitterCancellation(UUID sitterId);
}
