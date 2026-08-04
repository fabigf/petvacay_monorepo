package com.petvacay.booking.domain.model;

import com.petvacay.shared.domain.Money;

import java.math.BigDecimal;

/**
 * Servicio de dominio puro que calcula el desglose economico de una reserva.
 *
 * <p>Reglas (negocio.md seccion 3):
 * <ul>
 *   <li>precio base = tarifa/noche x noches x numero de mascotas</li>
 *   <li>el cliente paga el 100% del base + una tasa de gestion del 5%</li>
 *   <li>el cuidador cobra el 85% del base (retencion del 15%)</li>
 *   <li>el ingreso de la plataforma es la suma de ambas comisiones</li>
 * </ul>
 */
public final class PricingPolicy {

    /** Retencion aplicada al cuidador sobre el precio base. */
    public static final BigDecimal SITTER_COMMISSION_RATE = new BigDecimal("15");

    /** Tasa de gestion cargada al cliente sobre el precio base. */
    public static final BigDecimal CLIENT_SERVICE_FEE_RATE = new BigDecimal("5");

    private PricingPolicy() {
    }

    public static PricingBreakdown quote(Money pricePerNight, long nights, int petCount) {
        if (nights <= 0) {
            throw new IllegalArgumentException("La reserva debe cubrir al menos una noche");
        }
        if (petCount <= 0) {
            throw new IllegalArgumentException("La reserva debe incluir al menos una mascota");
        }

        Money basePrice = nightlyRate(pricePerNight, petCount).multiply(nights);
        Money clientFee = basePrice.percentage(CLIENT_SERVICE_FEE_RATE);
        Money sitterCommission = basePrice.percentage(SITTER_COMMISSION_RATE);

        return new PricingBreakdown(
                basePrice,
                clientFee,
                basePrice.plus(clientFee),
                sitterCommission,
                basePrice.minus(sitterCommission),
                clientFee.plus(sitterCommission));
    }

    /** Importe de una sola noche, usado como compensacion en cancelaciones tardias. */
    public static Money nightlyRate(Money pricePerNight, int petCount) {
        return pricePerNight.multiply(petCount);
    }
}
