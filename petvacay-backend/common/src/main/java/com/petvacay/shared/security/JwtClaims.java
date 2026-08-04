package com.petvacay.shared.security;

/**
 * Nombres de los claims del JWT de PetVacay.
 *
 * <p>Es el contrato entre quien emite los tokens (identity-service) y quien los
 * valida (los cuatro servicios y el Gateway). Vive en {@code common} por la misma
 * razon que los eventos de Kafka: es un contrato de integracion, y una constante
 * compartida evita que un typo en un literal deje a un servicio interpretando mal
 * los permisos de un usuario.
 */
public final class JwtClaims {

    /** Authorities del usuario: ROLE_CLIENT, ROLE_SITTER, ROLE_ADMIN. */
    public static final String ROLES = "roles";

    /**
     * Solo presente para cuidadores: indica si superaron el KYC.
     *
     * <p>Va en el token porque especificaciones.md (seccion 2.2) lo exige para
     * decidir el acceso a las vistas de cuidador sin una consulta extra. Es una
     * denormalizacion consciente: un cuidador recien verificado seguira teniendo
     * {@code is_verified=false} en su token hasta que caduque o vuelva a iniciar
     * sesion. Es aceptable porque el efecto es restrictivo (ve menos de lo que
     * podria, nunca mas), y las decisiones que de verdad importan — que un
     * cuidador no verificado no reciba reservas — las toma el backend contra el
     * estado real, no contra este claim.
     */
    public static final String IS_VERIFIED = "is_verified";

    /** Nombre de pila, para que el frontend salude sin pedir el perfil completo. */
    public static final String FIRST_NAME = "first_name";

    private JwtClaims() {
    }
}
