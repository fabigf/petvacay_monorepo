package com.petvacay.payment.adapters.in.web;

import com.petvacay.payment.application.PaymentApplicationService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Recibe las notificaciones de Stripe (especificaciones.md sección 3, paso 4).
 *
 * <p>Este endpoint es <b>público</b> por necesidad: lo llama Stripe, no un
 * usuario con sesión. Eso lo convierte en el punto más expuesto del sistema —
 * cualquiera en internet puede enviarle un POST diciendo "esta reserva está
 * pagada". La única cosa que lo hace seguro es la <b>verificación de la firma</b>
 * {@code Stripe-Signature}: sin ella, confirmar un pago sería tan fácil como
 * mandar un JSON.
 *
 * <p>Por eso se recibe el cuerpo como {@code String} en crudo y no como objeto
 * ya deserializado: la firma se calcula sobre los bytes exactos que envió
 * Stripe, y dejar que Spring parsee y vuelva a serializar el JSON cambiaría esos
 * bytes y haría fallar la verificación.
 */
@RestController
@RequestMapping("/api/v1/payments/webhooks")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final PaymentApplicationService payments;
    private final String webhookSecret;

    public StripeWebhookController(PaymentApplicationService payments,
                                   @Value("${petvacay.payments.stripe.webhook-secret:}") String webhookSecret) {
        this.payments = payments;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/stripe")
    public ResponseEntity<Void> handle(@RequestBody String payload,
                                       @RequestHeader("Stripe-Signature") String signature) {
        if (webhookSecret.isBlank()) {
            // Arrancar sin secreto configurado y aceptar igualmente los eventos
            // seria peor que no tener webhook: se rechaza explicitamente.
            log.error("Webhook de Stripe recibido sin secreto configurado; se descarta");
            return ResponseEntity.status(503).build();
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Webhook con firma no valida descartado: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        // Stripe reintenta si no recibe un 2xx, asi que el mismo evento puede
        // llegar varias veces; el servicio de aplicacion lo descarta por eventId.
        switch (event.getType()) {
            case "payment_intent.succeeded" -> paymentIntentOf(event)
                    .ifPresent(intent -> payments.onPaymentSucceeded(event.getId(), intent.getId(),
                            intent.getMetadata().get("booking_id")));

            case "payment_intent.payment_failed" -> paymentIntentOf(event)
                    .ifPresent(intent -> payments.onPaymentFailed(event.getId(), intent.getId(),
                            intent.getMetadata().get("booking_id")));

            default -> log.debug("Evento de Stripe ignorado: {}", event.getType());
        }

        // Siempre 200 tras verificar la firma: un error nuestro procesando no debe
        // hacer que Stripe reintente indefinidamente un evento que ya aceptamos.
        return ResponseEntity.ok().build();
    }

    private Optional<PaymentIntent> paymentIntentOf(Event event) {
        return event.getDataObjectDeserializer().getObject()
                .filter(PaymentIntent.class::isInstance)
                .map(PaymentIntent.class::cast);
    }
}
