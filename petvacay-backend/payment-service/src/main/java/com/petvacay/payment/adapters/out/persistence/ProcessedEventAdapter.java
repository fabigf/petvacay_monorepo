package com.petvacay.payment.adapters.out.persistence;

import com.petvacay.payment.ports.out.ProcessedEventPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
public class ProcessedEventAdapter implements ProcessedEventPort {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventAdapter.class);

    private final ProcessedEventJpaRepository repository;
    private final Clock clock;

    public ProcessedEventAdapter(ProcessedEventJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Comprueba si el evento ya se proceso y, si no, lo registra.
     *
     * <p>Es un "consulta y luego escribe", que en general dejaria una ventana de
     * carrera entre ambos pasos. Aqui no la hay, y por diseno y no por
     * casualidad: los eventos se publican con el id del agregado como clave de
     * Kafka, asi que todos los de una misma reserva caen en la misma particion y
     * los consume un unico hilo, en orden. Dos entregas del mismo evento no
     * llegan a procesarse a la vez.
     *
     * <p>La clave primaria sigue siendo la ultima defensa: si esa garantia de
     * particionado se rompiera, la base de datos rechazaria el duplicado y el
     * {@code catch} lo trataria como reentrega en lugar de dejar pasar un apunte
     * repetido.
     *
     * <p>Y por que no basta con insertar y capturar el conflicto, que seria lo
     * natural: al ser el identificador un valor asignado y no generado,
     * {@code save()} de Spring Data considera la entidad ya existente y ejecuta
     * un {@code merge}, que sobrescribe en silencio en vez de fallar. La
     * restriccion de unicidad nunca llegaria a dispararse.
     */
    @Override
    public boolean markProcessed(UUID eventId, String eventName) {
        if (repository.existsById(eventId)) {
            log.debug("Evento {} ({}) ya procesado; se ignora la reentrega", eventId, eventName);
            return false;
        }

        try {
            repository.saveAndFlush(new ProcessedEventEntity(eventId, eventName, clock.instant()));
            return true;
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            log.debug("Evento {} ({}) registrado en paralelo; se ignora", eventId, eventName);
            return false;
        }
    }
}
