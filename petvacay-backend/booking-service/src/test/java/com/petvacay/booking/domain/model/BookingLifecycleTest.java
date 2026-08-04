package com.petvacay.booking.domain.model;

import com.petvacay.booking.domain.event.BookingEvents;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import com.petvacay.shared.domain.DateRange;
import com.petvacay.shared.domain.DomainEvent;
import com.petvacay.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Ciclo de vida de la reserva (negocio.md 4.2)")
class BookingLifecycleTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 10);
    private static final LocalDate END = LocalDate.of(2026, 8, 17);
    private static final Instant BEFORE_START = instantAt(LocalDate.of(2026, 8, 1));

    private static Instant instantAt(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static Booking newDraft() {
        return Booking.draft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(UUID.randomUUID()), DateRange.of(START, END),
                ServiceType.NIGHT_ACCOMMODATION, Money.eur("25.00"));
    }

    private static Booking confirmedBooking() {
        Booking booking = newDraft();
        booking.request(BEFORE_START);
        booking.acceptBySitter(BEFORE_START.plus(Duration.ofHours(2)));
        booking.confirmPayment(BEFORE_START.plus(Duration.ofHours(3)));
        return booking;
    }

    @Test
    @DisplayName("recorre el camino feliz hasta FINALIZADO")
    void happyPath() {
        Booking booking = confirmedBooking();
        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMADO);

        booking.start(instantAt(START));
        assertThat(booking.status()).isEqualTo(BookingStatus.EN_PROGRESO);

        booking.complete(instantAt(END).plus(Duration.ofHours(25)));
        assertThat(booking.status()).isEqualTo(BookingStatus.FINALIZADO);
    }

    @Test
    @DisplayName("emite ReservaSolicitada al enviar la solicitud y PagoConfirmado al pagar")
    void emitsDomainEvents() {
        Booking booking = newDraft();
        booking.request(BEFORE_START);

        List<DomainEvent> afterRequest = booking.pullPendingEvents();
        assertThat(afterRequest).hasSize(1);
        assertThat(afterRequest.get(0)).isInstanceOf(BookingEvents.ReservaSolicitadaEvent.class);

        booking.acceptBySitter(BEFORE_START.plus(Duration.ofHours(1)));
        booking.confirmPayment(BEFORE_START.plus(Duration.ofHours(2)));

        assertThat(booking.pullPendingEvents())
                .singleElement()
                .isInstanceOf(BookingEvents.PagoConfirmadoEvent.class);
    }

    @Test
    @DisplayName("no permite saltarse el pago para iniciar el servicio")
    void cannotSkipPayment() {
        Booking booking = newDraft();
        booking.request(BEFORE_START);

        assertThatThrownBy(() -> booking.start(instantAt(START)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("SOLICITADO");
    }

    @Nested
    @DisplayName("Expiracion de la solicitud a las 24h")
    class RequestExpiry {

        @Test
        @DisplayName("el cuidador no puede aceptar pasadas 24 horas")
        void sitterCannotAcceptAfterWindow() {
            Booking booking = newDraft();
            booking.request(BEFORE_START);

            assertThatThrownBy(() -> booking.acceptBySitter(BEFORE_START.plus(Duration.ofHours(25))))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                    .isEqualTo("ERR_BOOKING_REQUEST_EXPIRED");
        }

        @Test
        @DisplayName("el sistema la cancela solo cuando la ventana ha vencido")
        void systemExpiresOnlyAfterWindow() {
            Booking booking = newDraft();
            booking.request(BEFORE_START);

            assertThatThrownBy(() -> booking.expireRequest(BEFORE_START.plus(Duration.ofHours(23))))
                    .isInstanceOf(BusinessRuleViolationException.class);

            booking.expireRequest(BEFORE_START.plus(Duration.ofHours(25)));
            assertThat(booking.status()).isEqualTo(BookingStatus.CANCELADO);
            assertThat(booking.cancelledBy()).isEqualTo(CancellationActor.SYSTEM);
        }
    }

    @Nested
    @DisplayName("Escrow y disputas")
    class EscrowAndDisputes {

        @Test
        @DisplayName("los fondos no se liberan antes de 24h tras el fin del servicio")
        void payoutHeldFor24Hours() {
            Booking booking = confirmedBooking();
            booking.start(instantAt(START));

            assertThatThrownBy(() -> booking.complete(instantAt(END).plus(Duration.ofHours(23))))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                    .isEqualTo("ERR_BOOKING_PAYOUT_HOLD");
        }

        @Test
        @DisplayName("una disputa dentro de la ventana congela la reserva")
        void disputeFreezesBooking() {
            Booking booking = confirmedBooking();
            booking.start(instantAt(START));
            booking.openDispute(UUID.randomUUID(), "La mascota volvio con una herida", instantAt(END));

            assertThat(booking.status()).isEqualTo(BookingStatus.EN_DISPUTA);
            assertThatThrownBy(() -> booking.cancel(CancellationActor.CLIENT, instantAt(END)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                    .isEqualTo("ERR_BOOKING_IN_DISPUTE");
        }

        @Test
        @DisplayName("no se admiten disputas pasada la ventana de 24h")
        void disputeWindowCloses() {
            Booking booking = confirmedBooking();
            booking.start(instantAt(START));

            assertThatThrownBy(() -> booking.openDispute(UUID.randomUUID(), "tarde",
                    instantAt(END).plus(Duration.ofHours(25))))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                    .isEqualTo("ERR_DISPUTE_WINDOW_CLOSED");
        }
    }

    @Nested
    @DisplayName("Bloqueo de calendario")
    class CalendarBlocking {

        @Test
        @DisplayName("solo bloquea desde que la reserva esta pagada y hasta que finaliza")
        void blocksOnlyWhileFunded() {
            Booking booking = newDraft();
            assertThat(booking.blocksCalendar()).isFalse();

            booking.request(BEFORE_START);
            assertThat(booking.blocksCalendar()).isFalse();

            booking.acceptBySitter(BEFORE_START.plus(Duration.ofHours(1)));
            assertThat(booking.blocksCalendar()).isFalse();

            booking.confirmPayment(BEFORE_START.plus(Duration.ofHours(2)));
            assertThat(booking.blocksCalendar()).isTrue();

            booking.start(instantAt(START));
            booking.complete(instantAt(END).plus(Duration.ofHours(25)));
            assertThat(booking.blocksCalendar()).isFalse();
        }
    }
}
