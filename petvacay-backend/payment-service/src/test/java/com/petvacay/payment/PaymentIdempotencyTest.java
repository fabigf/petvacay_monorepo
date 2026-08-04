package com.petvacay.payment;

import com.petvacay.booking.domain.event.BookingEvents;
import com.petvacay.payment.application.SettlementHandler;
import com.petvacay.payment.ports.out.LedgerRepositoryPort;
import com.petvacay.payment.ports.out.PaymentGatewayPort;
import com.petvacay.shared.domain.DateRange;
import com.petvacay.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cierra la limitación que la Fase 3 documentó: los consumidores de Kafka no
 * eran idempotentes.
 *
 * <p>Kafka garantiza entrega "al menos una vez". En un servicio que mueve dinero
 * eso no es un detalle: una reentrega duplicaría los apuntes del libro mayor y
 * descuadraría las cuentas sin que nada fallara de forma visible.
 */
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("payment-service: idempotencia y conversión de importes")
class PaymentIdempotencyTest {

    @Autowired
    private SettlementHandler settlement;

    @Autowired
    private LedgerRepositoryPort ledger;

    @Autowired
    private PaymentGatewayPort gateway;

    @Test
    @DisplayName("reentregar el mismo evento no duplica los apuntes del libro mayor")
    void duplicateEventIsIgnored() {
        BookingEvents.PagoConfirmadoEvent event = pagoConfirmado();

        settlement.onPagoConfirmado(event);
        settlement.onPagoConfirmado(event);
        settlement.onPagoConfirmado(event);

        assertThat(ledger.findByBookingId(event.bookingId()))
                .as("tres entregas del mismo evento deben producir un unico apunte")
                .hasSize(1);
    }

    @Test
    @DisplayName("dos eventos distintos de la misma reserva sí se registran ambos")
    void differentEventsAreBothProcessed() {
        UUID bookingId = UUID.randomUUID();
        settlement.onPagoConfirmado(pagoConfirmado(bookingId));
        settlement.onReservaFinalizada(new BookingEvents.ReservaFinalizadaEvent(
                UUID.randomUUID(), Instant.now(), bookingId, UUID.randomUUID(),
                sieteNoches(), 1, Money.eur("148.75"), Money.eur("35.00")));

        // ESCROW_HOLD + SITTER_PAYOUT + PLATFORM_REVENUE
        assertThat(ledger.findByBookingId(bookingId)).hasSize(3);
    }

    /**
     * La idempotencia de la pasarela: reintentar el cobro de la misma reserva
     * debe reutilizar el intento existente, no crear un cobro paralelo.
     */
    @Test
    @DisplayName("dos cobros con la misma clave de idempotencia devuelven el mismo intento")
    void gatewayIsIdempotent() {
        UUID bookingId = UUID.randomUUID();
        String key = "pay-" + bookingId;

        var first = gateway.createPaymentIntent(bookingId, UUID.randomUUID(), Money.eur("183.75"), key);
        var second = gateway.createPaymentIntent(bookingId, UUID.randomUUID(), Money.eur("183.75"), key);

        assertThat(second.paymentIntentId()).isEqualTo(first.paymentIntentId());
        assertThat(second.clientSecret()).isEqualTo(first.clientSecret());
    }

    private BookingEvents.PagoConfirmadoEvent pagoConfirmado() {
        return pagoConfirmado(UUID.randomUUID());
    }

    private BookingEvents.PagoConfirmadoEvent pagoConfirmado(UUID bookingId) {
        return new BookingEvents.PagoConfirmadoEvent(
                UUID.randomUUID(), Instant.now(), bookingId, UUID.randomUUID(), UUID.randomUUID(),
                sieteNoches(), 1, Money.eur("183.75"), Instant.now().plusSeconds(86_400));
    }

    private DateRange sieteNoches() {
        LocalDate start = LocalDate.now().plusDays(30);
        return DateRange.of(start, start.plusDays(7));
    }
}
