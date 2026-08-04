package com.petvacay.identity.domain.model;

/**
 * Catalogo de servicios que un cuidador puede ofrecer (negocio.md seccion 2:
 * "Define sus propios servicios... Configura de forma autonoma sus tarifas").
 *
 * <p>Deliberadamente distinto de {@code booking.domain.model.ServiceType}
 * (privado de booking-service) aunque comparten los mismos tres valores: uno
 * describe el catalogo que el cuidador publica, el otro que servicio eligio el
 * cliente al reservar. Mismo criterio de duplicacion en el limite que el resto
 * de contratos entre servicios (ver README, "Contrato interno
 * identity-service -> booking-service").
 */
public enum ServiceType {
    NIGHT_ACCOMMODATION,
    DAY_CARE,
    WALKING
}
