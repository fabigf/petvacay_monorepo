package com.petvacay.payment.adapters.out.stripe;

import com.petvacay.payment.ports.out.PaymentGatewayPort;
import com.petvacay.shared.domain.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pasarela simulada para desarrollo y pruebas.
 *
 * <p>Existe por una razón concreta: levantar el entorno local no debería exigir
 * una cuenta de Stripe ni claves de API. Sustituye las llamadas de red por
 * identificadores generados en memoria, de modo que el resto del sistema —
 * libro mayor, eventos, máquina de estados — se ejercita igual.
 *
 * <p><b>Lo que esta clase NO demuestra:</b> que la integración real funcione. Un
 * simulador solo prueba el código propio. Verificar la integración de verdad
 * exige claves de prueba de Stripe y ejercitar
 * {@link StripePaymentGatewayAdapter} contra su entorno de test; mientras eso no
 * se haga, la integración con Stripe está implementada pero **no verificada**.
 *
 * <p>Sí reproduce la idempotencia, que es el comportamiento del que depende la
 * corrección del sistema: repetir una operación con la misma clave devuelve el
 * resultado anterior en lugar de crear uno nuevo.
 */
@Component
@ConditionalOnProperty(name = "petvacay.payments.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGatewayAdapter.class);

    private final Map<String, PaymentIntent> intentsByKey = new ConcurrentHashMap<>();
    private final Map<String, TransferResult> transfersByKey = new ConcurrentHashMap<>();
    private final Map<String, RefundResult> refundsByKey = new ConcurrentHashMap<>();

    @Override
    public PaymentIntent createPaymentIntent(UUID bookingId, UUID clientId, Money amount,
                                             String idempotencyKey) {
        return intentsByKey.computeIfAbsent(idempotencyKey, key -> {
            String intentId = "pi_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            log.info("[SIMULADO] PaymentIntent {} por {} para la reserva {}", intentId, amount, bookingId);
            // El client_secret real de Stripe tiene la forma "{intentId}_secret_{aleatorio}".
            return new PaymentIntent(intentId, intentId + "_secret_simulado", amount);
        });
    }

    @Override
    public TransferResult transferToSitter(UUID bookingId, String sitterAccountId, Money amount,
                                           String idempotencyKey) {
        return transfersByKey.computeIfAbsent(idempotencyKey, key -> {
            String transferId = "tr_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            log.info("[SIMULADO] Transferencia {} de {} al cuidador ({})", transferId, amount, sitterAccountId);
            return new TransferResult(transferId, amount);
        });
    }

    @Override
    public RefundResult refund(String paymentIntentId, Money amount, String idempotencyKey) {
        return refundsByKey.computeIfAbsent(idempotencyKey, key -> {
            String refundId = "re_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            log.info("[SIMULADO] Reembolso {} de {} sobre {}", refundId, amount, paymentIntentId);
            return new RefundResult(refundId, amount);
        });
    }
}
