package com.petvacay.payment.adapters.in.events;

import com.petvacay.booking.domain.event.BookingEvents;
import com.petvacay.payment.application.SettlementHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada que escucha el bus interno de Spring y delega en
 * {@link SettlementHandler}. Su homologo para Kafka es
 * {@code SettlementKafkaListener}; ambos son mutuamente excluyentes.
 */
@Component
@ConditionalOnProperty(name = "petvacay.messaging.provider", havingValue = "spring", matchIfMissing = true)
public class SettlementEventListener {

    private final SettlementHandler handler;

    public SettlementEventListener(SettlementHandler handler) {
        this.handler = handler;
    }

    @EventListener
    public void on(BookingEvents.PagoConfirmadoEvent event) {
        handler.onPagoConfirmado(event);
    }

    @EventListener
    public void on(BookingEvents.ReservaFinalizadaEvent event) {
        handler.onReservaFinalizada(event);
    }

    @EventListener
    public void on(BookingEvents.ReservaCanceladaEvent event) {
        handler.onReservaCancelada(event);
    }
}
