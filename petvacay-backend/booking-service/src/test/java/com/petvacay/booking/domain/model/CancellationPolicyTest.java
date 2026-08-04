package com.petvacay.booking.domain.model;

import com.petvacay.booking.domain.model.CancellationOutcome.CancellationPolicyKind;
import com.petvacay.shared.domain.DateRange;
import com.petvacay.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Politicas de cancelacion y reembolso (negocio.md 4.3)")
class CancellationPolicyTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 10);
    private static final LocalDate END = LocalDate.of(2026, 8, 17);
    private static final Instant SERVICE_START = START.atStartOfDay().toInstant(ZoneOffset.UTC);

    private static Booking confirmedBooking(int petCount) {
        List<UUID> petIds = java.util.stream.IntStream.range(0, petCount)
                .mapToObj(i -> UUID.randomUUID()).toList();

        Booking booking = Booking.draft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                petIds, DateRange.of(START, END), ServiceType.NIGHT_ACCOMMODATION, Money.eur("25.00"));

        Instant early = SERVICE_START.minus(Duration.ofDays(20));
        booking.request(early);
        booking.acceptBySitter(early.plus(Duration.ofHours(1)));
        booking.confirmPayment(early.plus(Duration.ofHours(2)));
        booking.pullPendingEvents();
        return booking;
    }

    @Test
    @DisplayName("cancelacion con mas de 48h: devuelve la tarifa base y retiene el 5%")
    void clientCancelsEarly() {
        Booking booking = confirmedBooking(1);
        booking.cancel(CancellationActor.CLIENT, SERVICE_START.minus(Duration.ofHours(72)));

        CancellationOutcome outcome = booking.cancellationOutcome();
        assertThat(outcome.policyApplied()).isEqualTo(CancellationPolicyKind.CLIENT_EARLY);
        assertThat(outcome.refundToClient()).isEqualTo(Money.eur("175.00"));
        assertThat(outcome.payoutToSitter()).isEqualTo(Money.eur("0.00"));
        assertThat(outcome.platformRevenue()).isEqualTo(Money.eur("8.75"));
    }

    @Test
    @DisplayName("cancelacion dentro de 48h: la primera noche compensa al cuidador, ya neta del 15%")
    void clientCancelsLate() {
        Booking booking = confirmedBooking(1);
        booking.cancel(CancellationActor.CLIENT, SERVICE_START.minus(Duration.ofHours(12)));

        CancellationOutcome outcome = booking.cancellationOutcome();
        assertThat(outcome.policyApplied()).isEqualTo(CancellationPolicyKind.CLIENT_LATE);
        assertThat(outcome.refundToClient()).isEqualTo(Money.eur("150.00"));
        // 25,00 de la primera noche menos el 15% de retencion
        assertThat(outcome.payoutToSitter()).isEqualTo(Money.eur("21.25"));
        // tasa de gestion 8,75 + comision de 3,75 sobre la noche compensada
        assertThat(outcome.platformRevenue()).isEqualTo(Money.eur("12.50"));
    }

    @Test
    @DisplayName("compensar una noche cancelada deja al cuidador indemne, no mejorado")
    void compensationEqualsWhatTheNightWouldHaveEarned() {
        Booking booking = confirmedBooking(1);

        // Lo que el cuidador habria cobrado por una sola noche prestada
        BigDecimal perWorkedNight = booking.pricing().sitterPayout().amount()
                .divide(BigDecimal.valueOf(booking.dates().nights()), 2, RoundingMode.HALF_UP);

        booking.cancel(CancellationActor.CLIENT, SERVICE_START.minus(Duration.ofHours(12)));

        assertThat(booking.cancellationOutcome().payoutToSitter().amount())
                .isEqualByComparingTo(perWorkedNight);
    }

    @Test
    @DisplayName("ninguna politica crea ni destruye dinero")
    void moneyIsConservedInEveryPolicy() {
        for (CancellationActor actor : new CancellationActor[]{CancellationActor.CLIENT, CancellationActor.SITTER}) {
            for (long hoursBeforeStart : new long[]{72, 12}) {
                Booking booking = confirmedBooking(1);
                booking.cancel(actor, SERVICE_START.minus(Duration.ofHours(hoursBeforeStart)));

                CancellationOutcome outcome = booking.cancellationOutcome();
                Money distributed = outcome.refundToClient()
                        .plus(outcome.payoutToSitter())
                        .plus(outcome.platformRevenue());

                assertThat(distributed)
                        .as("actor=%s, %dh antes del inicio", actor, hoursBeforeStart)
                        .isEqualTo(booking.pricing().totalChargedToClient());
            }
        }
    }

    @Test
    @DisplayName("la compensacion de primera noche escala con el numero de mascotas")
    void lateCancellationScalesWithPets() {
        Booking booking = confirmedBooking(2);
        booking.cancel(CancellationActor.CLIENT, SERVICE_START.minus(Duration.ofHours(12)));

        // 2 mascotas x 25,00 = 50,00, menos el 15%
        assertThat(booking.cancellationOutcome().payoutToSitter()).isEqualTo(Money.eur("42.50"));
    }

    @Test
    @DisplayName("si cancela el cuidador el cliente recupera el 100%, tasas incluidas")
    void sitterCancels() {
        Booking booking = confirmedBooking(1);
        booking.cancel(CancellationActor.SITTER, SERVICE_START.minus(Duration.ofHours(12)));

        CancellationOutcome outcome = booking.cancellationOutcome();
        assertThat(outcome.policyApplied()).isEqualTo(CancellationPolicyKind.SITTER_CANCELLED);
        assertThat(outcome.refundToClient()).isEqualTo(Money.eur("183.75"));
        assertThat(outcome.platformRevenue()).isEqualTo(Money.eur("0.00"));
    }

    @Test
    @DisplayName("una solicitud aun no pagada se cancela sin movimiento de dinero")
    void unfundedCancellationMovesNoMoney() {
        Booking booking = Booking.draft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(UUID.randomUUID()), DateRange.of(START, END),
                ServiceType.NIGHT_ACCOMMODATION, Money.eur("25.00"));
        booking.request(SERVICE_START.minus(Duration.ofDays(10)));

        booking.cancel(CancellationActor.CLIENT, SERVICE_START.minus(Duration.ofDays(9)));

        CancellationOutcome outcome = booking.cancellationOutcome();
        assertThat(outcome.policyApplied()).isEqualTo(CancellationPolicyKind.NOT_YET_FUNDED);
        assertThat(outcome.refundToClient().isZero()).isTrue();
        assertThat(outcome.payoutToSitter().isZero()).isTrue();
    }
}
