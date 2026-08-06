package com.petvacay.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fija la aritmetica del tipo sobre el que descansa todo el dinero de la
 * plataforma.
 *
 * <p>Es codigo minusculo y por eso mismo facil de dar por supuesto, pero un
 * redondeo distinto aqui desplaza a la vez el precio que ve el cliente, el pago
 * al cuidador, la comision y cada asiento del libro mayor — y lo hace en
 * silencio, porque las cuentas seguirian pareciendo razonables.
 */
@DisplayName("Money: aritmetica monetaria")
class MoneyTest {

    @Test
    @DisplayName("normaliza siempre a 2 decimales con HALF_UP")
    void normalizesToTwoDecimals() {
        assertThat(Money.eur("10.005").amount()).isEqualByComparingTo("10.01");
        assertThat(Money.eur("10.004").amount()).isEqualByComparingTo("10.00");
        // La escala se fija tambien cuando sobran ceros: 10 y 10.00 son el mismo
        // importe y deben poder compararse como iguales.
        assertThat(Money.eur("10")).isEqualTo(Money.eur("10.00"));
    }

    @Test
    @DisplayName("suma y resta conservan la divisa")
    void addsAndSubtracts() {
        Money veinticinco = Money.eur("25.00");

        assertThat(veinticinco.plus(Money.eur("5.50")).amount()).isEqualByComparingTo("30.50");
        assertThat(veinticinco.minus(Money.eur("5.50")).amount()).isEqualByComparingTo("19.50");
        assertThat(veinticinco.plus(Money.eur("1.00")).currency()).isEqualTo(Money.EUR);
    }

    @Test
    @DisplayName("restar mas de lo que hay da un importe negativo, no un error")
    void subtractionCanGoNegative() {
        Money resultado = Money.eur("10.00").minus(Money.eur("25.00"));

        assertThat(resultado.isNegative()).isTrue();
        assertThat(resultado.amount()).isEqualByComparingTo("-15.00");
    }

    @Test
    @DisplayName("multiplicar escala el importe por noches o mascotas")
    void multipliesByFactor() {
        assertThat(Money.eur("25.00").multiply(7).amount()).isEqualByComparingTo("175.00");
        assertThat(Money.eur("25.00").multiply(0).isZero()).isTrue();
    }

    @Test
    @DisplayName("el porcentaje redondea a 2 decimales")
    void appliesPercentage() {
        // 15% de 175,00 = 26,25 exacto.
        assertThat(Money.eur("175.00").percentage(new BigDecimal("15")).amount())
                .isEqualByComparingTo("26.25");
        // 5% de 175,00 = 8,75 exacto.
        assertThat(Money.eur("175.00").percentage(new BigDecimal("5")).amount())
                .isEqualByComparingTo("8.75");
        // Un caso que no cae exacto: 15% de 33,33 = 4,9995 -> 5,00.
        assertThat(Money.eur("33.33").percentage(new BigDecimal("15")).amount())
                .isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("operar importes de distinta divisa se rechaza en vez de mezclarlos")
    void refusesToMixCurrencies() {
        Money euros = Money.eur("10.00");
        Money dolares = new Money(new BigDecimal("10.00"), Currency.getInstance("USD"));

        assertThatThrownBy(() -> euros.plus(dolares))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinta divisa");
        assertThatThrownBy(() -> euros.minus(dolares))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> euros.compareTo(dolares))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ordena por importe dentro de la misma divisa")
    void comparesByAmount() {
        assertThat(Money.eur("10.00")).isLessThan(Money.eur("25.00"));
        assertThat(Money.eur("25.00")).isGreaterThan(Money.eur("10.00"));
        assertThat(Money.eur("25.00")).isEqualByComparingTo(Money.eur("25.00"));
    }

    @Test
    @DisplayName("cero es cero y no es negativo")
    void zeroIsNeitherNegativeNorPositive() {
        Money cero = Money.zero(Money.EUR);

        assertThat(cero.isZero()).isTrue();
        assertThat(cero.isNegative()).isFalse();
    }
}
