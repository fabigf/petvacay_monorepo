package com.petvacay.payment.application;

import com.petvacay.payment.domain.model.BookingParties;
import com.petvacay.payment.ports.out.BookingPartiesRepositoryPort;
import com.petvacay.payment.ports.out.PaymentGatewayPort;
import com.petvacay.payment.ports.out.ProcessedEventPort;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import com.petvacay.shared.domain.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orquesta el cobro de una reserva contra la pasarela (Fase 6).
 *
 * <p>Hasta ahora, "pagar" era una transición de estado que el cliente disparaba
 * a mano; a partir de esta fase hay dinero real de por medio y el flujo pasa a
 * ser el que describe especificaciones.md sección 3: se crea un
 * {@code PaymentIntent}, React cobra la tarjeta contra Stripe, y el resultado
 * llega por webhook.
 */
@Service
public class PaymentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    private final PaymentGatewayPort gateway;
    private final BookingPartiesRepositoryPort bookingParties;
    private final ProcessedEventPort processedEvents;

    public PaymentApplicationService(PaymentGatewayPort gateway,
                                     BookingPartiesRepositoryPort bookingParties,
                                     ProcessedEventPort processedEvents) {
        this.gateway = gateway;
        this.bookingParties = bookingParties;
        this.processedEvents = processedEvents;
    }

    /**
     * Prepara el cobro y devuelve el {@code clientSecret} para Stripe Elements.
     *
     * <p>El importe **no** llega del cliente: se recibe desde booking-service,
     * que es quien lo calculó con las reglas de dominio. Aceptar aquí una
     * cantidad enviada por el navegador permitiría pagar 1 € por una reserva de
     * 183,75 €.
     *
     * <p>La clave de idempotencia se deriva del identificador de la reserva, de
     * modo que reintentar el pago de la misma reserva reutiliza el
     * {@code PaymentIntent} existente en lugar de crear cobros paralelos.
     */
    @Transactional
    public PaymentGatewayPort.PaymentIntent preparePayment(UUID bookingId, UUID actingUserId, Money amount) {
        BookingParties parties = bookingParties.findByBookingId(bookingId)
                .orElseThrow(() -> BusinessRuleViolationException.conflict(
                        "ERR_BOOKING_NOT_PAYABLE", "Reserva no disponible para pago",
                        "Esta reserva todavía no está lista para el cobro."));

        if (!parties.clientId().equals(actingUserId)) {
            throw new BusinessRuleViolationException("ERR_NOT_BOOKING_PARTICIPANT", "Acceso denegado",
                    403, "Solo el cliente de la reserva puede pagarla.");
        }

        return gateway.createPaymentIntent(bookingId, parties.clientId(), amount,
                "pay-" + bookingId);
    }

    /**
     * Stripe confirma el cobro.
     *
     * <p>Stripe reintenta los webhooks que no responden 2xx, así que el mismo
     * evento puede llegar varias veces; se descarta por su identificador con el
     * mismo mecanismo que los eventos de Kafka.
     */
    @Transactional
    public void onPaymentSucceeded(String stripeEventId, String paymentIntentId, String bookingId) {
        if (!markProcessed(stripeEventId, "stripe.payment_intent.succeeded")) {
            return;
        }
        log.info("Stripe confirma el cobro {} de la reserva {}", paymentIntentId, bookingId);
        // La transicion de la reserva a CONFIRMADO la ejecuta booking-service al
        // recibir este hecho; aqui solo queda registrado el cobro externo.
    }

    @Transactional
    public void onPaymentFailed(String stripeEventId, String paymentIntentId, String bookingId) {
        if (!markProcessed(stripeEventId, "stripe.payment_intent.payment_failed")) {
            return;
        }
        log.warn("Stripe informa de un cobro fallido {} en la reserva {}", paymentIntentId, bookingId);
    }

    /**
     * Los identificadores de evento de Stripe son cadenas ({@code evt_...}), no
     * UUID, así que se derivan a UUID de forma determinista para poder
     * reutilizar la misma tabla de idempotencia que los eventos de Kafka.
     */
    private boolean markProcessed(String stripeEventId, String eventName) {
        UUID derived = UUID.nameUUIDFromBytes(stripeEventId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return processedEvents.markProcessed(derived, eventName);
    }
}
