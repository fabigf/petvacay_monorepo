package com.petvacay.booking.domain.model;

/**
 * Quien deja la resena, no a quien va dirigida. Deliberadamente distinto de
 * {@link CancellationActor} aunque comparte dos valores: mezclar ambos
 * conceptos confundiria "quien cancelo" con "quien valora", que no son la
 * misma decision ni ocurren en el mismo momento del ciclo de vida.
 */
public enum ReviewAuthorRole {
    CLIENT,
    SITTER
}
