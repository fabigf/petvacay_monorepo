package com.petvacay.booking.domain.model;

/**
 * Estados del ciclo de vida de una reserva (negocio.md seccion 4.2).
 * Los nombres son parte del contrato publico consumido por React.
 */
public enum BookingStatus {

    BORRADOR,
    SOLICITADO,
    PENDIENTE_PAGO,
    CONFIRMADO,
    EN_PROGRESO,
    FINALIZADO,
    CANCELADO,
    EN_DISPUTA;

    public boolean isTerminal() {
        return this == FINALIZADO || this == CANCELADO;
    }

    /** Estados en los que el dinero del cliente ya esta cobrado y en custodia (escrow). */
    public boolean isFunded() {
        return this == CONFIRMADO || this == EN_PROGRESO || this == EN_DISPUTA || this == FINALIZADO;
    }
}
