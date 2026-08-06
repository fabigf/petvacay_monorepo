package com.petvacay.booking.domain.model;

import com.petvacay.shared.domain.DateRange;
import com.petvacay.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fija la franja de entrega y recogida documentada en negocio.md 4.2.
 *
 * <p>Lo que se prueba no es aritmetica sino una decision de producto: se entrega
 * el dia que empieza la reserva y se recoge el dia que termina — el de fin, aunque
 * esa noche ya no se cobre — dentro de una franja fija de 09:00 a 20:00. Es
 * justo el punto donde el intervalo semiabierto de {@link DateRange} se presta a
 * confusion: la ultima noche facturada es la anterior al fin, pero al animal se
 * le recoge el dia de fin.
 */
@DisplayName("Franja de entrega y recogida (negocio.md 4.2)")
class HandoffWindowTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 13);
    private static final LocalDate END = LocalDate.of(2026, 8, 20);

    @Test
    @DisplayName("entrega el dia de inicio y recoge el dia de fin de la reserva")
    void mirrorsTheBookingDates() {
        HandoffWindow window = HandoffWindow.forBooking(bookingFor(START, END));

        assertThat(window.dropOffDate()).isEqualTo(START);
        assertThat(window.pickUpDate()).isEqualTo(END);
    }

    @Test
    @DisplayName("aplica la franja generica de 09:00 a 20:00 mientras no se modele el horario del cuidador")
    void appliesTheDefaultTimeWindow() {
        HandoffWindow window = HandoffWindow.forBooking(bookingFor(START, END));

        assertThat(window.windowStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(window.windowEnd()).isEqualTo(LocalTime.of(20, 0));
        assertThat(window.windowStart()).isBefore(window.windowEnd());
    }

    @Test
    @DisplayName("la recogida es el dia de fin, no la ultima noche facturada")
    void pickUpIsTheEndDateNotTheLastBilledNight() {
        Booking booking = bookingFor(START, END);
        HandoffWindow window = HandoffWindow.forBooking(booking);

        // Se cobran 7 noches (13..19) pero se recoge el dia 20.
        assertThat(booking.dates().nights()).isEqualTo(7);
        assertThat(booking.dates().contains(window.pickUpDate())).isFalse();
        assertThat(window.pickUpDate()).isEqualTo(window.dropOffDate().plusDays(7));
    }

    @Test
    @DisplayName("una reserva de una sola noche entrega y recoge en dias consecutivos")
    void singleNightBooking() {
        HandoffWindow window = HandoffWindow.forBooking(bookingFor(START, START.plusDays(1)));

        assertThat(window.dropOffDate()).isEqualTo(START);
        assertThat(window.pickUpDate()).isEqualTo(START.plusDays(1));
    }

    private static Booking bookingFor(LocalDate start, LocalDate end) {
        return Booking.draft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(UUID.randomUUID()), DateRange.of(start, end),
                ServiceType.NIGHT_ACCOMMODATION, Money.eur("25.00"));
    }
}
