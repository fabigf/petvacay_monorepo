package com.petvacay.booking.domain.model;

import com.petvacay.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Modelo de comision doble (negocio.md 3.1)")
class PricingPolicyTest {

    @Test
    @DisplayName("reproduce el ejemplo del contrato de API: 25 EUR x 7 noches = 183,75 EUR")
    void reproducesApiContractExample() {
        PricingBreakdown pricing = PricingPolicy.quote(Money.eur("25.00"), 7, 1);

        assertThat(pricing.basePrice()).isEqualTo(Money.eur("175.00"));
        assertThat(pricing.clientServiceFee()).isEqualTo(Money.eur("8.75"));
        assertThat(pricing.totalChargedToClient()).isEqualTo(Money.eur("183.75"));
    }

    @Test
    @DisplayName("retiene el 15% al cuidador y consolida ambas comisiones como ingreso")
    void splitsCommissionsBetweenSitterAndPlatform() {
        PricingBreakdown pricing = PricingPolicy.quote(Money.eur("25.00"), 7, 1);

        assertThat(pricing.sitterCommission()).isEqualTo(Money.eur("26.25"));
        assertThat(pricing.sitterPayout()).isEqualTo(Money.eur("148.75"));
        assertThat(pricing.platformRevenue()).isEqualTo(Money.eur("35.00"));
    }

    @Test
    @DisplayName("el reparto cuadra: lo cobrado al cliente = pago al cuidador + ingreso plataforma")
    void moneyIsConserved() {
        PricingBreakdown pricing = PricingPolicy.quote(Money.eur("33.33"), 5, 2);

        assertThat(pricing.sitterPayout().plus(pricing.platformRevenue()))
                .isEqualTo(pricing.totalChargedToClient());
    }

    @Test
    @DisplayName("la tarifa se multiplica por el numero de mascotas")
    void scalesWithPetCount() {
        PricingBreakdown one = PricingPolicy.quote(Money.eur("20.00"), 3, 1);
        PricingBreakdown three = PricingPolicy.quote(Money.eur("20.00"), 3, 3);

        assertThat(one.basePrice()).isEqualTo(Money.eur("60.00"));
        assertThat(three.basePrice()).isEqualTo(Money.eur("180.00"));
    }

    @Test
    @DisplayName("rechaza reservas sin noches o sin mascotas")
    void rejectsDegenerateQuotes() {
        assertThatThrownBy(() -> PricingPolicy.quote(Money.eur("20.00"), 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PricingPolicy.quote(Money.eur("20.00"), 3, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
