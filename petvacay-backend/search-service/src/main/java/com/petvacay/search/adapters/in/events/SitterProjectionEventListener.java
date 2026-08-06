package com.petvacay.search.adapters.in.events;

import com.petvacay.booking.domain.event.BookingEvents;
import com.petvacay.identity.domain.event.IdentityEvents;
import com.petvacay.search.application.SitterProjectionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada que escucha el bus interno de Spring
 * ({@code petvacay.messaging.provider=spring}, por defecto en desarrollo) y delega
 * en {@link SitterProjectionHandler}. Su homologo para Kafka es
 * {@code SitterProjectionKafkaListener}; ambos son mutuamente excluyentes.
 */
@Component
@ConditionalOnProperty(name = "petvacay.messaging.provider", havingValue = "spring", matchIfMissing = true)
public class SitterProjectionEventListener {

    private final SitterProjectionHandler handler;

    public SitterProjectionEventListener(SitterProjectionHandler handler) {
        this.handler = handler;
    }

    @EventListener
    public void on(IdentityEvents.CuidadorIndexadoEvent event) {
        handler.onCuidadorIndexado(event);
    }

    @EventListener
    public void on(IdentityEvents.CuidadorRechazadoEvent event) {
        handler.onCuidadorRechazado(event);
    }

    @EventListener
    public void on(BookingEvents.PagoConfirmadoEvent event) {
        handler.onPagoConfirmado(event);
    }

    @EventListener
    public void on(BookingEvents.ReservaCanceladaEvent event) {
        handler.onReservaCancelada(event);
    }

    @EventListener
    public void on(BookingEvents.ReservaFinalizadaEvent event) {
        handler.onReservaFinalizada(event);
    }

    @EventListener
    public void on(BookingEvents.DisputaResueltaEvent event) {
        handler.onDisputaResuelta(event);
    }

    @EventListener
    public void on(BookingEvents.ResenaCreadaEvent event) {
        handler.onResenaCreada(event);
    }

    @EventListener
    public void on(BookingEvents.ReputacionRecalculadaEvent event) {
        handler.onReputacionRecalculada(event);
    }
}
