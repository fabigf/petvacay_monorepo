package com.petvacay.payment.domain.model;

import com.petvacay.shared.domain.Money;

import java.util.UUID;

/**
 * Lo que payment-service sabe con certeza de una reserva sin preguntarle a
 * nadie: quiénes son sus dos partes y cuánto hay que cobrar.
 *
 * <p>El dato vive en booking-service, así que hay dos formas de obtenerlo —
 * preguntar por HTTP en cada consulta, o quedarse con el que ya viaja en los
 * eventos de reserva. Se eligió lo segundo, por el mismo razonamiento que llevó
 * a {@code CuidadorIndexadoEvent} en la Fase 4: {@code ReservaSolicitadaEvent}
 * ya transporta {@code clientId}, {@code sitterId} y {@code amountToPay}, y
 * guardarlos aquí mantiene a payment-service capaz de responder aunque
 * booking-service esté caído.
 *
 * <p>{@code amountToPay} es la <b>única</b> fuente válida del importe a cobrar.
 * Antes se aceptaba el que enviaba el navegador, lo que permitía pedir un cobro
 * de 0,01 € para una reserva de 588,00 €: el precio lo decide el dominio de
 * reservas, nunca el cliente.
 */
public record BookingParties(UUID bookingId, UUID clientId, UUID sitterId, Money amountToPay) {

    /** El libro mayor de una reserva solo lo pueden ver sus dos partes. */
    public boolean includes(UUID userId) {
        return clientId.equals(userId) || sitterId.equals(userId);
    }

    /**
     * ¿Coincide lo que el cliente cree que va a pagar con lo que de verdad se le
     * va a cobrar? Se compara por importe y divisa.
     */
    public boolean matchesAmount(Money claimed) {
        return amountToPay != null
                && amountToPay.currency().equals(claimed.currency())
                && amountToPay.amount().compareTo(claimed.amount()) == 0;
    }
}
