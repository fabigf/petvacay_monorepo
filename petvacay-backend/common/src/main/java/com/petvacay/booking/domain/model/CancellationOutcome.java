package com.petvacay.booking.domain.model;

import com.petvacay.shared.domain.Money;

/**
 * Resultado economico de una cancelacion: cuanto se devuelve al cliente,
 * cuanto se compensa al cuidador y que retiene la plataforma.
 */
public record CancellationOutcome(
        CancellationPolicyKind policyApplied,
        Money refundToClient,
        Money payoutToSitter,
        Money platformRevenue) {

    public enum CancellationPolicyKind {
        /** La reserva aun no estaba pagada: no hay movimiento de dinero. */
        NOT_YET_FUNDED,
        /** Cliente cancela con mas de 48h de antelacion. */
        CLIENT_EARLY,
        /** Cliente cancela dentro de las 48h previas al inicio. */
        CLIENT_LATE,
        /** Cancela el cuidador: reembolso integro al cliente. */
        SITTER_CANCELLED,
        /**
         * Un administrador resolvio una disputa (negocio.md 4.4). A diferencia de
         * las demas, esta no la decide una politica automatica: el reparto sale de
         * lo que dictamine el equipo tras analizar las pruebas.
         */
        DISPUTE_RESOLVED
    }
}
