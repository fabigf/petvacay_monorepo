package com.petvacay.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fija la semantica de intervalo semiabierto [inicio, fin).
 *
 * <p>Es la regla de la que dependen a la vez el precio (cuantas noches se
 * cobran), el bloqueo de calendario (que noches quedan ocupadas) y la
 * disponibilidad que ve el cliente. Tratar el fin como incluido cobraria una
 * noche de mas y bloquearia un dia que en realidad esta libre — el clasico error
 * de un dia, que ademas pasa desapercibido salvo justo en los bordes.
 */
@DisplayName("DateRange: intervalo [inicio, fin)")
class DateRangeTest {

    private static final LocalDate DIA_10 = LocalDate.of(2026, 8, 10);
    private static final LocalDate DIA_13 = LocalDate.of(2026, 8, 13);
    private static final LocalDate DIA_20 = LocalDate.of(2026, 8, 20);

    @Test
    @DisplayName("las noches son la diferencia en dias, no los dias del calendario")
    void nightsAreTheDifference() {
        assertThat(DateRange.of(DIA_13, DIA_20).nights()).isEqualTo(7);
        assertThat(DateRange.of(DIA_13, DIA_13.plusDays(1)).nights()).isEqualTo(1);
    }

    @Test
    @DisplayName("exige que el fin sea posterior al inicio")
    void rejectsDegenerateRanges() {
        assertThatThrownBy(() -> DateRange.of(DIA_13, DIA_13))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior");
        assertThatThrownBy(() -> DateRange.of(DIA_20, DIA_13))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("contiene el dia de inicio pero NO el de fin")
    void containsIsHalfOpen() {
        DateRange rango = DateRange.of(DIA_13, DIA_20);

        assertThat(rango.contains(DIA_13)).isTrue();
        assertThat(rango.contains(DIA_20.minusDays(1))).isTrue();
        // El dia de fin es cuando se recoge a la mascota: esa noche ya no se
        // pasa en casa del cuidador, asi que ni se cobra ni bloquea plaza.
        assertThat(rango.contains(DIA_20)).isFalse();
        assertThat(rango.contains(DIA_13.minusDays(1))).isFalse();
    }

    @Test
    @DisplayName("dos rangos solapan si comparten al menos una noche")
    void detectsOverlap() {
        DateRange base = DateRange.of(DIA_13, DIA_20);

        assertThat(base.overlaps(DateRange.of(DIA_10, DIA_20))).isTrue();
        assertThat(base.overlaps(DateRange.of(DIA_13.plusDays(2), DIA_13.plusDays(4)))).isTrue();
        assertThat(base.overlaps(base)).isTrue();
    }

    @Test
    @DisplayName("encadenar reservas no cuenta como solape: una acaba el dia que empieza la otra")
    void backToBackRangesDoNotOverlap() {
        DateRange primera = DateRange.of(DIA_13, DIA_20);
        DateRange segunda = DateRange.of(DIA_20, DIA_20.plusDays(3));

        // Es el caso que hace util el intervalo semiabierto: el dia 20 se recoge
        // una mascota y se entrega la siguiente, y la plaza da para las dos.
        assertThat(primera.overlaps(segunda)).isFalse();
        assertThat(segunda.overlaps(primera)).isFalse();
    }

    @Test
    @DisplayName("rangos completamente separados no solapan")
    void disjointRangesDoNotOverlap() {
        assertThat(DateRange.of(DIA_10, DIA_13).overlaps(DateRange.of(DIA_20, DIA_20.plusDays(2))))
                .isFalse();
    }

    @Test
    @DisplayName("no admite fechas nulas")
    void rejectsNulls() {
        assertThatThrownBy(() -> DateRange.of(null, DIA_20))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DateRange.of(DIA_13, null))
                .isInstanceOf(NullPointerException.class);
    }
}
