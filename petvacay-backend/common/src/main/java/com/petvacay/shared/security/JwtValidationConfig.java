package com.petvacay.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;

/**
 * Validacion de JWT compartida por los cuatro servicios (Fase 5).
 *
 * <p>Todos verifican la firma con la misma clave publica RS256 y traducen el claim
 * {@code roles} a authorities de Spring Security de forma identica; lo que cada
 * servicio decide por su cuenta son las **reglas** de autorizacion, en su propio
 * {@code SecurityFilterChain}. Esa es la linea: aqui vive el "como se lee un
 * token", no el "quien puede hacer que".
 *
 * <p>La clave publica se lee de un recurso empaquetado, de modo que la validacion
 * es completamente offline: ningun servicio necesita que identity-service este
 * levantado para rechazar un token invalido. Ver
 * {@code identity-service/src/main/resources/keys/README.md} para el razonamiento
 * completo y el compromiso que implica frente a JWKS.
 */
@Configuration
public class JwtValidationConfig {

    private final Resource publicKeyLocation;

    public JwtValidationConfig(
            @Value("${petvacay.jwt.public-key-location:classpath:keys/jwt-dev-public.pem}")
            Resource publicKeyLocation) {
        this.publicKeyLocation = publicKeyLocation;
    }

    @Bean
    public JwtDecoder jwtDecoder() throws Exception {
        return NimbusJwtDecoder.withPublicKey(readPublicKey()).build();
    }

    /**
     * Traduce el claim {@code roles} (una lista de strings) a authorities.
     *
     * <p>Se usa prefijo vacio porque los roles ya viajan con el prefijo
     * {@code ROLE_} desde el dominio ({@code Role.ROLE_CLIENT}); el convertidor
     * por defecto de Spring anadiria un segundo prefijo y produciria
     * {@code SCOPE_ROLE_CLIENT}, que no casaria con ningun {@code hasRole()}.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtClaims.ROLES);
        authorities.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * {@code RsaKeyConverters} lee PEM en formato X.509 SubjectPublicKeyInfo con
     * las clases del propio JDK. Se prefiere a
     * {@code RSAKey.parseFromPEMEncodedObjects} de Nimbus, que parece equivalente
     * pero exige BouncyCastle en el classpath y falla en tiempo de arranque si no
     * esta.
     */
    private RSAPublicKey readPublicKey() throws Exception {
        try (InputStream in = publicKeyLocation.getInputStream()) {
            return RsaKeyConverters.x509().convert(in);
        }
    }
}
