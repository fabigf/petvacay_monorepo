package com.petvacay.booking.domain.model;

import com.petvacay.shared.domain.Money;

/**
 * Desglose economico de una reserva bajo el modelo de comision doble
 * (negocio.md seccion 3.1): 15% retenido al cuidador y 5% cargado al cliente.
 */
public record PricingBreakdown(
        Money basePrice,
        Money clientServiceFee,
        Money totalChargedToClient,
        Money sitterCommission,
        Money sitterPayout,
        Money platformRevenue) {

    public PricingBreakdown {
        if (basePrice.isNegative()) {
            throw new IllegalArgumentException("El precio base no puede ser negativo");
        }
    }
}
