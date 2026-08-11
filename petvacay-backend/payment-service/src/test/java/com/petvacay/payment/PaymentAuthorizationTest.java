package com.petvacay.payment;

import com.petvacay.booking.domain.event.BookingEvents;
import com.petvacay.payment.application.PaymentApplicationService;
import com.petvacay.payment.application.SettlementHandler;
import com.petvacay.payment.ports.out.PaymentGatewayPort;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import com.petvacay.shared.domain.DateRange;
import com.petvacay.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fija quién puede pagar una reserva y por cuánto.
 *
 * <p>Cubre una vulnerabilidad real: {@code preparePayment} pasaba a la pasarela
 * el importe que venía en el cuerpo de la petición, así que enviar
 * {@code {"amount": 0.01}} generaba un cobro de un céntimo por una reserva de
 * cientos de euros. El javadoc afirmaba que el importe "no llega del cliente",
 * pero ninguna comprobación lo respaldaba y ningún test lo cubría.
 */
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("payment-service: autorización e integridad del importe")
class PaymentAuthorizationTest {

    @Autowired
    private PaymentApplicationService payments;

    @Autowired
    private SettlementHandler settlement;

    private UUID bookingId;
    private UUID clientId;
    private UUID sitterId;

    private static final Money PRECIO_REAL = Money.eur("588.00");

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        sitterId = UUID.randomUUID();

        // Es el evento que la reserva emite al solicitarse: trae el importe que
        // calculo el dominio de booking-service.
        settlement.onReservaSolicitada(new BookingEvents.ReservaSolicitadaEvent(
                UUID.randomUUID(), Instant.now(), bookingId, clientId, sitterId,
                sieteNoches(), PRECIO_REAL));
    }

    @Test
    @DisplayName("el cliente no puede pagar menos de lo que cuesta la reserva")
    void clientCannotUnderpay() {
        assertThatThrownBy(() -> payments.preparePayment(bookingId, clientId, Money.eur("0.01")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(error -> ((BusinessRuleViolationException) error).getCode())
                .isEqualTo("ERR_PAYMENT_AMOUNT_MISMATCH");
    }

    @Test
    @DisplayName("tampoco puede pagar de mas sin enterarse")
    void clientCannotOverpayEither() {
        // Cobrar de mas en silencio es tan inaceptable como cobrar de menos:
        // significaria que la pantalla enseña un precio y se carga otro.
        assertThatThrownBy(() -> payments.preparePayment(bookingId, clientId, Money.eur("999.00")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(error -> ((BusinessRuleViolationException) error).getCode())
                .isEqualTo("ERR_PAYMENT_AMOUNT_MISMATCH");
    }

    @Test
    @DisplayName("el cobro se crea por el importe real de la reserva")
    void chargesTheAuthoritativeAmount() {
        PaymentGatewayPort.PaymentIntent intent =
                payments.preparePayment(bookingId, clientId, PRECIO_REAL);

        assertThat(intent.amount()).isEqualTo(PRECIO_REAL);
        assertThat(intent.clientSecret()).isNotBlank();
    }

    @Test
    @DisplayName("una divisa distinta no cuela aunque el numero coincida")
    void currencyMustMatchToo() {
        Money mismosNumerosOtraDivisa = new Money(
                PRECIO_REAL.amount(), java.util.Currency.getInstance("USD"));

        assertThatThrownBy(() -> payments.preparePayment(bookingId, clientId, mismosNumerosOtraDivisa))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(error -> ((BusinessRuleViolationException) error).getCode())
                .isEqualTo("ERR_PAYMENT_AMOUNT_MISMATCH");
    }

    @Test
    @DisplayName("solo el cliente de la reserva puede pagarla")
    void onlyTheClientCanPay() {
        assertThatThrownBy(() -> payments.preparePayment(bookingId, sitterId, PRECIO_REAL))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(error -> ((BusinessRuleViolationException) error).getCode())
                .isEqualTo("ERR_NOT_BOOKING_PARTICIPANT");

        assertThatThrownBy(() -> payments.preparePayment(bookingId, UUID.randomUUID(), PRECIO_REAL))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(error -> ((BusinessRuleViolationException) error).getCode())
                .isEqualTo("ERR_NOT_BOOKING_PARTICIPANT");
    }

    @Test
    @DisplayName("no se puede preparar el cobro de una reserva desconocida")
    void unknownBookingIsNotPayable() {
        assertThatThrownBy(() -> payments.preparePayment(UUID.randomUUID(), clientId, PRECIO_REAL))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(error -> ((BusinessRuleViolationException) error).getCode())
                .isEqualTo("ERR_BOOKING_NOT_PAYABLE");
    }

    @Test
    @DisplayName("el importe se conserva tal cual lo calculo el dominio de reservas")
    void amountSurvivesTheEventRoundTrip() {
        // Con centimos que no caen redondos, para descartar un redondeo por el
        // camino entre el evento, la base de datos y la pasarela.
        UUID otra = UUID.randomUUID();
        UUID suCliente = UUID.randomUUID();
        settlement.onReservaSolicitada(new BookingEvents.ReservaSolicitadaEvent(
                UUID.randomUUID(), Instant.now(), otra, suCliente, UUID.randomUUID(),
                sieteNoches(), Money.eur("183.75")));

        PaymentGatewayPort.PaymentIntent intent =
                payments.preparePayment(otra, suCliente, Money.eur("183.75"));

        assertThat(intent.amount().amount()).isEqualByComparingTo("183.75");
    }

    private DateRange sieteNoches() {
        LocalDate start = LocalDate.now().plusDays(30);
        return DateRange.of(start, start.plusDays(7));
    }
}
