package com.petvacay.payment.adapters.in.web;

import com.petvacay.payment.application.PaymentApplicationService;
import com.petvacay.payment.ports.out.PaymentGatewayPort;
import com.petvacay.shared.domain.Money;
import com.petvacay.shared.security.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

/**
 * Inicia el cobro de una reserva y entrega el {@code clientSecret} a React.
 *
 * <p>Es el paso 1 de especificaciones.md seccion 3: el backend crea el
 * PaymentIntent, el navegador monta Stripe Elements con ese secreto y la tarjeta
 * viaja directamente a Stripe sin pasar por aqui.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentIntentController {

    private final PaymentApplicationService payments;

    public PaymentIntentController(PaymentApplicationService payments) {
        this.payments = payments;
    }

    @PostMapping("/bookings/{bookingId}/intent")
    public PaymentIntentResponse createIntent(@PathVariable UUID bookingId,
                                              @AuthenticatedUser UUID actingUserId,
                                              @RequestBody CreateIntentRequest request) {
        Money amount = new Money(request.amount(), Currency.getInstance(request.currency()));
        PaymentGatewayPort.PaymentIntent intent = payments.preparePayment(bookingId, actingUserId, amount);
        return new PaymentIntentResponse(intent.paymentIntentId(), intent.clientSecret(),
                intent.amount().amount(), intent.amount().currency().getCurrencyCode());
    }

    /**
     * El importe se valida contra el que booking-service calculo; NO es un dato
     * que el navegador pueda elegir libremente (ver PaymentApplicationService).
     */
    public record CreateIntentRequest(
            @NotNull @Positive(message = "El importe debe ser mayor que cero") BigDecimal amount,
            @NotNull(message = "La divisa es obligatoria") String currency) {
    }

    public record PaymentIntentResponse(String paymentIntentId, String clientSecret,
                                        BigDecimal amount, String currency) {
    }
}
