package com.petvacay.booking.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Franja de entrega y recogida de la mascota (negocio.md 4.2, "Franja de
 * entrega y recogida").
 *
 * <p>No estaba especificada en el diseño original de negocio: se fija aquí como
 * decision de producto al implementar la entrega de la direccion al cliente. La
 * mascota se entrega el dia de inicio de la reserva y se recoge el dia de fin,
 * ambos dentro de la misma franja horaria fija — 09:00 a 20:00 — porque el
 * sistema no modela todavia el horario real de cada cuidador. Cuando exista esa
 * informacion, esta clase es el unico sitio que habria que tocar para dejar de
 * usar la franja generica.
 */
public record HandoffWindow(LocalDate dropOffDate, LocalDate pickUpDate, LocalTime windowStart,
                            LocalTime windowEnd) {

    public static final LocalTime DEFAULT_WINDOW_START = LocalTime.of(9, 0);
    public static final LocalTime DEFAULT_WINDOW_END = LocalTime.of(20, 0);

    public static HandoffWindow forBooking(Booking booking) {
        return new HandoffWindow(booking.dates().start(), booking.dates().end(),
                DEFAULT_WINDOW_START, DEFAULT_WINDOW_END);
    }
}
