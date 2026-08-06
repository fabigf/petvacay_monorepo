package com.petvacay.booking.domain.model;

/** Estado de atencion de una incidencia por parte del equipo de administracion. */
public enum IncidentStatus {

    /** Registrada y a la espera de que alguien la atienda. */
    ABIERTA,

    /** Atendida: una disputa dictaminada o una alerta medica ya gestionada. */
    RESUELTA
}
