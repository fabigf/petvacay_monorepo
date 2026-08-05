package com.petvacay.booking.domain.model;

/**
 * Quien deja la resena, no a quien va dirigida. Deliberadamente distinto de
 * {@link CancellationActor} aunque comparte dos valores: mezclar ambos
 * conceptos confundiria "quien cancelo" con "quien valora", que no son la
 * misma decision ni ocurren en el mismo momento del ciclo de vida.
 */
public enum ReviewAuthorRole {
    CLIENT,
    SITTER,
    /**
     * La plataforma. Solo la usa la resena negativa automatica que genera la
     * cancelacion de un cuidador sobre una reserva ya confirmada (negocio.md
     * 4.3): no la escribe ninguna persona, y por eso no consume el cupo de
     * ninguna de las dos partes — el cliente afectado puede dejar la suya igual.
     */
    SYSTEM
}
