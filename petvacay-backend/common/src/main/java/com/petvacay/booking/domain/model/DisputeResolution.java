package com.petvacay.booking.domain.model;

/**
 * Sentido en el que un administrador resuelve una disputa (negocio.md 4.4).
 *
 * <p>Vive en {@code common} y no en booking-service porque viaja dentro de
 * {@code DisputaResueltaEvent} hasta payment-service, que necesita deserializarlo.
 */
public enum DisputeResolution {

    /** Se da la razon al cliente: se le devuelve todo lo que pago. */
    FAVOR_CLIENT,

    /** Se da la razon al cuidador: el escrow se reparte como en una reserva normal. */
    FAVOR_SITTER,

    /**
     * Resolucion intermedia: el administrador fija cuanto se le devuelve al
     * cliente y el resto se liquida con las comisiones habituales.
     */
    SPLIT
}
