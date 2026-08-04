package com.petvacay.identity.domain.model;

/**
 * Estados del proceso KYC (negocio.md seccion 4.1).
 * Solo {@code VERIFICADO} hace al cuidador indexable en el buscador.
 */
public enum VerificationStatus {

    PENDIENTE_VERIFICACION,
    VERIFICADO,
    RECHAZADO
}
