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
}
