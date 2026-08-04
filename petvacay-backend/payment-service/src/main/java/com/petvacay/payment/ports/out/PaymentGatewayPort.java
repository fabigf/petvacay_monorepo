package com.petvacay.payment.ports.out;

import com.petvacay.shared.domain.Money;

import java.util.UUID;

/**
 * SPI hacia la pasarela de pago (Fase 6).
 *
 * <p>El dominio de pagos habla de "cobrar", "reembolsar" y "transferir al
 * cuidador"; no sabe que detrás hay Stripe. Esa frontera es lo que permite
 * tener un adaptador simulado para desarrollo y otro real para producción sin
 * duplicar ninguna regla de negocio, y lo que haría que cambiar de proveedor
 * fuese sustituir una clase.
 *
 * <p>Todas las operaciones reciben una **clave de idempotencia**. No es
 * opcional: si la red corta la respuesta de un cobro, reintentar sin esa clave
 * cobraría dos veces al cliente. Con ella, la pasarela reconoce el reintento y
 * devuelve el resultado de la operación original.
 */
public interface PaymentGatewayPort {

    /**
     * Crea la intención de cobro y devuelve el {@code clientSecret} con el que
     * React monta el formulario de tarjeta.
     *
     * <p>Los datos de la tarjeta nunca pasan por PetVacay: van del navegador
     * directamente a Stripe (especificaciones.md sección 3). Es lo que mantiene
     * el sistema fuera del alcance más exigente de PCI-DSS.
     */
    PaymentIntent createPaymentIntent(UUID bookingId, UUID clientId, Money amount, String idempotencyKey);

    /** Transfiere al cuidador su liquidación una vez liberado el escrow. */
    TransferResult transferToSitter(UUID bookingId, String sitterAccountId, Money amount,
                                    String idempotencyKey);

    /** Reembolsa (total o parcialmente) un cobro ya realizado. */
    RefundResult refund(String paymentIntentId, Money amount, String idempotencyKey);

    record PaymentIntent(String paymentIntentId, String clientSecret, Money amount) {
    }

    record TransferResult(String transferId, Money amount) {
    }

    record RefundResult(String refundId, Money amount) {
    }
}
