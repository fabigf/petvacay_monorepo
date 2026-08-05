package com.petvacay.booking.adapters.out.identity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Token del usuario que origino la peticion en curso, para reenviarlo a
 * identity-service.
 *
 * <p>Se propaga la credencial del usuario en lugar de una propia del servicio
 * porque mantiene la trazabilidad de extremo a extremo (en los logs de
 * identity-service consta en nombre de quien se hizo la consulta) y evita
 * introducir un segundo mecanismo de credenciales para maquinas. El limite
 * conocido es que solo funciona dentro del contexto de una peticion de usuario;
 * un proceso en segundo plano — el scheduler del ciclo de vida, por ejemplo —
 * necesitaria un flujo client-credentials aparte.
 */
final class PropagatedUserToken {

    private PropagatedUserToken() {
    }

    static String current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getTokenValue();
        }
        throw new IllegalStateException(
                "No hay un JWT en el contexto de seguridad: identity-service rechazaria la consulta.");
    }
}
