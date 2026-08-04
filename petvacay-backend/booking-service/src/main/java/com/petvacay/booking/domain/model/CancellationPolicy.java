package com.petvacay.booking.domain.model;

import com.petvacay.shared.domain.Money;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Politicas de cancelacion y reembolso (negocio.md seccion 4.3).
 *
 * <p>Decisiones tomadas donde el documento de negocio no es explicito:
 * <ul>
 *   <li>La tasa de gestion del 5% es no reembolsable en toda cancelacion iniciada
 *       por el cliente. Retribuye un servicio ya prestado (emparejamiento, apertura
 *       del escrow y soporte) y se devenga al reservar. Devolverla en el caso tardio
 *       haria que cancelar tarde saliese mejor que cancelar pronto.</li>
 *   <li>La compensacion de la primera noche si soporta la retencion del 15%. Una
 *       compensacion debe dejar al cuidador indemne, no mejorado: con la retencion
 *       cobra exactamente lo que habria ganado de haberse prestado esa noche. Sin
 *       ella, una noche cancelada le rentaria mas que una noche trabajada.</li>
 * </ul>
 */
public final class CancellationPolicy {

    /** Frontera entre cancelacion anticipada y de ultima hora. */
    public static final Duration LATE_CANCELLATION_WINDOW = Duration.ofHours(48);

    private CancellationPolicy() {
    }

    public static CancellationOutcome resolve(Booking booking, CancellationActor actor, Instant now) {
        PricingBreakdown pricing = booking.pricing();
        Money zero = Money.zero(pricing.basePrice().currency());

        if (!booking.status().isFunded()) {
            return new CancellationOutcome(
                    CancellationOutcome.CancellationPolicyKind.NOT_YET_FUNDED, zero, zero, zero);
        }

        if (actor == CancellationActor.SITTER) {
            return new CancellationOutcome(
                    CancellationOutcome.CancellationPolicyKind.SITTER_CANCELLED,
                    pricing.totalChargedToClient(),
                    zero,
                    zero);
        }

        if (isEarly(booking, now)) {
            return new CancellationOutcome(
                    CancellationOutcome.CancellationPolicyKind.CLIENT_EARLY,
                    pricing.basePrice(),
                    zero,
                    pricing.clientServiceFee());
        }

        // El cliente pierde el importe de la primera noche, que compensa al cuidador
        // por el bloqueo de su calendario con la misma retencion del 15% de siempre.
        Money firstNight = PricingPolicy.nightlyRate(booking.pricePerNight(), booking.petIds().size());
        Money refund = pricing.basePrice().minus(firstNight);
        if (refund.isNegative()) {
            refund = zero;
            firstNight = pricing.basePrice();
        }

        Money commissionOnFirstNight = firstNight.percentage(PricingPolicy.SITTER_COMMISSION_RATE);

        return new CancellationOutcome(
                CancellationOutcome.CancellationPolicyKind.CLIENT_LATE,
                refund,
                firstNight.minus(commissionOnFirstNight),
                pricing.clientServiceFee().plus(commissionOnFirstNight));
    }

    private static boolean isEarly(Booking booking, Instant now) {
        Instant serviceStart = booking.dates().start().atStartOfDay().toInstant(ZoneOffset.UTC);
        return Duration.between(now, serviceStart).compareTo(LATE_CANCELLATION_WINDOW) > 0;
    }
}
