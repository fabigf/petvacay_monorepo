package com.petvacay.booking.domain.model;

/** Naturaleza de la incidencia (negocio.md 4.4 y 5). */
public enum IncidentType {

    /**
     * Disputa formal abierta por una de las partes. Congela la liberacion de
     * fondos hasta que el equipo de administracion dicte resolucion.
     */
    DISPUTA,

    /**
     * Alerta medica dada por el cuidador. No congela nada: el servicio sigue en
     * curso y lo que hace falta es coordinar asistencia, no retener dinero.
     */
    ALERTA_MEDICA
}
