package com.petvacay.booking.adapters.in.scheduler;

import com.petvacay.booking.ports.in.ManageBookingUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada temporal: dispara las transiciones que no nacen de una
 * accion del usuario (negocio.md seccion 4.2, expiracion de solicitudes a 24h).
 */
@Component
public class BookingLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingLifecycleScheduler.class);

    private final ManageBookingUseCase manageBooking;

    public BookingLifecycleScheduler(ManageBookingUseCase manageBooking) {
        this.manageBooking = manageBooking;
    }

    /** Cancela las solicitudes que el cuidador dejo sin responder mas de 24 horas. */
    @Scheduled(fixedDelayString = "${petvacay.scheduler.expire-requests-ms:300000}")
    public void expireStaleRequests() {
        int expired = manageBooking.expireStaleRequests();
        if (expired > 0) {
            log.info("{} solicitudes canceladas automaticamente por expiracion de 24h", expired);
        }
    }

    /** Arranca el servicio de las reservas confirmadas cuya fecha de inicio ya llego. */
    @Scheduled(fixedDelayString = "${petvacay.scheduler.advance-lifecycle-ms:300000}")
    public void startDueBookings() {
        int started = manageBooking.startDueBookings();
        if (started > 0) {
            log.info("{} reservas pasadas a EN_PROGRESO al alcanzar su fecha de inicio", started);
        }
    }

    /**
     * Libera el escrow de las reservas cuyo servicio termino hace mas de 24 horas
     * sin disputa (negocio.md seccion 3.2). Sin esto el cuidador no cobra nunca
     * por si solo: la transicion a FINALIZADO — y el evento que la liquidacion
     * escucha — dependia de que alguien llamara al endpoint a mano.
     */
    @Scheduled(fixedDelayString = "${petvacay.scheduler.advance-lifecycle-ms:300000}")
    public void releaseDuePayouts() {
        int released = manageBooking.releaseDuePayouts();
        if (released > 0) {
            log.info("{} reservas finalizadas automaticamente; fondos liberados al cuidador", released);
        }
    }
}
