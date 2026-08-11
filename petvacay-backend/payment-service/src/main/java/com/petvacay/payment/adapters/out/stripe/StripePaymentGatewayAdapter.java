package com.petvacay.payment.adapters.out.stripe;

import com.petvacay.payment.ports.out.PaymentGatewayPort;
import com.petvacay.shared.domain.Money;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Integración real con Stripe Connect (Fase 6).
 *
 * <p>Los datos de tarjeta nunca tocan este servicio: se crea un
 * {@code PaymentIntent} y se devuelve su {@code clientSecret}, con el que React
 * monta el formulario de Stripe Elements. El navegador envía la tarjeta
 * directamente a Stripe, y PetVacay se entera del resultado por webhook
 * (especificaciones.md sección 3). Esa separación es lo que mantiene el sistema
 * fuera del alcance más exigente de PCI-DSS.
 */
@Component
@ConditionalOnProperty(name = "petvacay.payments.provider", havingValue = "stripe")
public class StripePaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGatewayAdapter.class);

    private final StripeClient stripe;

    /**
     * @param apiBase destino de las llamadas. Vacío — lo normal — deja el que
     *                trae el SDK ({@code https://api.stripe.com}). Se puede
     *                apuntar a otra URL para dirigir este mismo adaptador contra
     *                {@code stripe-mock}, el servidor de pruebas oficial de
     *                Stripe, y comprobar así que las peticiones que construye
     *                son válidas sin necesidad de una cuenta ni de claves
     *                reales. Es la única forma de ejercitar este código: el
     *                adaptador simulado prueba el resto del sistema, pero no
     *                esta clase.
     */
    public StripePaymentGatewayAdapter(
            @Value("${petvacay.payments.stripe.secret-key}") String secretKey,
            @Value("${petvacay.payments.stripe.api-base:}") String apiBase) {

        StripeClient.StripeClientBuilder builder = StripeClient.builder().setApiKey(secretKey);
        if (!apiBase.isBlank()) {
            log.warn("Stripe apuntando a {} en lugar de la API real", apiBase);
            builder.setApiBase(apiBase);
        }
        this.stripe = builder.build();
    }

    @Override
    public PaymentGatewayPort.PaymentIntent createPaymentIntent(UUID bookingId, UUID clientId,
                                                                Money amount, String idempotencyKey) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(toMinorUnits(amount))
                    .setCurrency(amount.currency().getCurrencyCode().toLowerCase())
                    // Retención en custodia: se captura ahora y el reparto al
                    // cuidador se hace despues, al liberar el escrow.
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                    .putMetadata("booking_id", bookingId.toString())
                    .putMetadata("client_id", clientId.toString())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .build();

            // Nombre cualificado: `PaymentIntent` existe tanto en Stripe como en
            // nuestro puerto, y son cosas distintas a proposito — el del puerto
            // solo expone los tres datos que el resto del sistema necesita.
            com.stripe.model.PaymentIntent intent =
                    stripe.paymentIntents().create(params, idempotent(idempotencyKey));

            log.info("PaymentIntent {} creado para la reserva {}", intent.getId(), bookingId);
            return new PaymentGatewayPort.PaymentIntent(intent.getId(), intent.getClientSecret(), amount);
        } catch (StripeException e) {
            throw new StripeOperationException("No se pudo iniciar el cobro de la reserva " + bookingId, e);
        }
    }

    @Override
    public TransferResult transferToSitter(UUID bookingId, String sitterAccountId, Money amount,
                                           String idempotencyKey) {
        try {
            TransferCreateParams params = TransferCreateParams.builder()
                    .setAmount(toMinorUnits(amount))
                    .setCurrency(amount.currency().getCurrencyCode().toLowerCase())
                    .setDestination(sitterAccountId)
                    .putAllMetadata(Map.of("booking_id", bookingId.toString()))
                    .build();

            Transfer transfer = stripe.transfers().create(params, idempotent(idempotencyKey));
            return new TransferResult(transfer.getId(), amount);
        } catch (StripeException e) {
            throw new StripeOperationException(
                    "No se pudo transferir la liquidación de la reserva " + bookingId, e);
        }
    }

    @Override
    public RefundResult refund(String paymentIntentId, Money amount, String idempotencyKey) {
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setAmount(toMinorUnits(amount))
                    .build();

            Refund refund = stripe.refunds().create(params, idempotent(idempotencyKey));
            return new RefundResult(refund.getId(), amount);
        } catch (StripeException e) {
            throw new StripeOperationException("No se pudo reembolsar el cobro " + paymentIntentId, e);
        }
    }

    /**
     * La clave de idempotencia es obligatoria en toda operación que mueva dinero.
     *
     * <p>Si la respuesta de Stripe se pierde por un corte de red, el reintento
     * llega con la misma clave y Stripe devuelve el resultado de la operación
     * original en lugar de cobrar dos veces.
     */
    private RequestOptions idempotent(String idempotencyKey) {
        return RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
    }

    /**
     * Stripe trabaja en la unidad mínima de la divisa (céntimos para el euro).
     *
     * <p>Se multiplica sobre {@link BigDecimal}, nunca sobre {@code double}:
     * {@code 183.75 * 100} en coma flotante da 18374.999... y truncar produciría
     * un céntimo de menos en cada cobro.
     */
    private long toMinorUnits(Money amount) {
        return amount.amount()
                .movePointRight(amount.currency().getDefaultFractionDigits())
                .longValueExact();
    }

    /** Error de pasarela; el manejador global lo traduce a RFC 7807. */
    public static class StripeOperationException extends RuntimeException {

        public StripeOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
