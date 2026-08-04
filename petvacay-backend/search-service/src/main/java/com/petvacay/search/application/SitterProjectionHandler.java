package com.petvacay.search.application;

import com.petvacay.booking.domain.event.BookingEvents;
import com.petvacay.booking.domain.model.CancellationOutcome;
import com.petvacay.identity.domain.event.IdentityEvents;
import com.petvacay.search.domain.model.SitterView;
import com.petvacay.search.ports.out.SitterReadModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Mantiene sincronizado el modelo de lectura del buscador a partir de los eventos
 * de dominio: es el lado "query" del CQRS.
 *
 * <p>Es logica de aplicacion pura, sin ninguna anotacion de mensajeria: no sabe si
 * quien la invoca escucho el bus interno de Spring o un consumidor de Kafka. Esa
 * decision vive en los adaptadores de entrada
 * ({@code adapters/in/events/SitterProjectionEventListener} y
 * {@code adapters/in/kafka/SitterProjectionKafkaListener}), que solo se encargan de
 * recibir el evento por su transporte y delegar aqui.
 *
 * <p>Hasta la Fase 3, {@code onCuidadorVerificado}/{@code onPerfilActualizado}
 * volvian a consultar los repositorios de {@code identity} para reconstruir la
 * vista, porque vivian en el mismo proceso. Desde la Fase 4, search-service es un
 * servicio independiente sin acceso a la base de datos de identity-service: por
 * eso ahora solo escucha {@link IdentityEvents.CuidadorIndexadoEvent}, el evento
 * de integracion que ya trae el snapshot completo (ver el javadoc de
 * {@code IdentityEvents}). Este handler no vuelve a leer nada de identity-service
 * en ningun punto.
 */
@Component
public class SitterProjectionHandler {

    private static final Logger log = LoggerFactory.getLogger(SitterProjectionHandler.class);

    private final SitterReadModelPort readModel;

    public SitterProjectionHandler(SitterReadModelPort readModel) {
        this.readModel = readModel;
    }

    /** Alta o reproyeccion completa: el cuidador ya viene verificado en el evento. */
    public void onCuidadorIndexado(IdentityEvents.CuidadorIndexadoEvent event) {
        SitterView existing = readModel.findById(event.sitterId()).orElse(null);

        // El buscador muestra "desde X€": la tarifa mas barata del catalogo del
        // cuidador, no un precio unico — un cuidador puede ofrecer varios
        // servicios con tarifas distintas (negocio.md seccion 2).
        BigDecimal startingPrice = event.servicePrices().values().stream()
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        readModel.upsert(new SitterView(
                event.sitterId(),
                event.firstName(),
                existing != null ? existing.rating() : BigDecimal.ZERO,
                existing != null ? existing.reviewCount() : 0,
                startingPrice,
                event.currency(),
                event.homeType(),
                event.latitude(),
                event.longitude(),
                event.maxSimultaneousPets(),
                event.acceptedSpecies(),
                event.servicePrices().keySet(),
                event.maxPetWeightKg(),
                event.photoUrl(),
                existing != null ? new HashMap<>(existing.occupancyByDate()) : new HashMap<>()));

        log.info("Vista de busqueda indexada para el cuidador {}", event.sitterId());
    }

    public void onCuidadorRechazado(IdentityEvents.CuidadorRechazadoEvent event) {
        readModel.remove(event.sitterId());
        log.info("Cuidador {} retirado del indice de busqueda tras rechazo de KYC", event.sitterId());
    }

    /** Un pago confirmado bloquea el calendario del cuidador para esas fechas. */
    public void onPagoConfirmado(BookingEvents.PagoConfirmadoEvent event) {
        readModel.blockDates(event.sitterId(), event.dates().start(), event.dates().end(), event.petCount());
    }

    /**
     * Solo se libera calendario si la reserva llego a estar pagada: una solicitud
     * cancelada antes del pago nunca bloqueo fechas.
     */
    public void onReservaCancelada(BookingEvents.ReservaCanceladaEvent event) {
        if (event.outcome().policyApplied() == CancellationOutcome.CancellationPolicyKind.NOT_YET_FUNDED) {
            return;
        }
        readModel.releaseDates(event.sitterId(), event.dates().start(), event.dates().end(), event.petCount());
    }

    public void onReservaFinalizada(BookingEvents.ReservaFinalizadaEvent event) {
        readModel.releaseDates(event.sitterId(), event.dates().start(), event.dates().end(), event.petCount());
    }

    /** Una resena del cliente hacia el cuidador actualiza su reputacion publica. */
    public void onResenaCreada(BookingEvents.ResenaCreadaEvent event) {
        readModel.recordReview(event.sitterId(), event.rating());
    }
}
