package com.petvacay.booking.domain.model;

/**
 * Naturaleza del aviso. El frontend la usa para el icono y el color; el texto
 * viaja ya redactado en la propia notificacion.
 */
public enum NotificationType {

    /** Al cuidador: tiene una solicitud esperando respuesta. */
    BOOKING_REQUESTED,
    /** Al cliente: el cuidador acepto y toca pagar. */
    BOOKING_ACCEPTED,
    /** Al cliente: el cuidador rechazo, o la solicitud caduco sin respuesta. */
    BOOKING_REJECTED,
    /** A ambas partes: el pago se confirmo y la reserva esta cerrada en firme. */
    BOOKING_CONFIRMED,
    /** A ambas partes: la reserva se cancelo. */
    BOOKING_CANCELLED,
    /** A ambas partes: el servicio termino y ya se puede valorar. */
    BOOKING_FINISHED,
    /** Al administrador: hay una incidencia esperando resolucion. */
    DISPUTE_OPENED,
    /** A ambas partes: el equipo de administracion resolvio la incidencia. */
    DISPUTE_RESOLVED,
    /** Al cliente y al equipo de soporte: alerta medica sobre la mascota. */
    PET_EMERGENCY
}
