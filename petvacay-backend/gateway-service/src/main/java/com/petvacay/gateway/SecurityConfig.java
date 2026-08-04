package com.petvacay.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;

/**
 * Validacion de JWT en el perimetro (Fase 5).
 *
 * <p>estructura.md asigna al Gateway la responsabilidad de validar el token, y
 * asi se hace: un token ausente, caducado o mal firmado se rechaza aqui sin
 * llegar a consumir capacidad de ningun servicio. Pero **los servicios validan
 * igualmente por su cuenta**, no confian en que alguien lo haya hecho antes. Esa
 * duplicacion es intencionada: si un atacante alcanzara la red interna, o si una
 * ruta futura se anadiera al Gateway sin protegerla, cada servicio seguiria
 * exigiendo un token valido. Delegar la autenticacion exclusivamente en el
 * perimetro convierte cualquier fallo del perimetro en un compromiso total.
 *
 * <p>El Gateway no depende de {@code common} (no conoce tipos de dominio), asi
 * que replica aqui la lectura de la clave publica en su variante reactiva:
 * WebFlux necesita {@code ReactiveJwtDecoder}, no el {@code JwtDecoder} servlet
 * que usan los cuatro servicios.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http, ProblemDetailSecurityHandlers problemDetailHandlers) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // El CorsWebFilter de CorsConfig ya cubre el preflight.
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchange -> exchange
                        // Preflight de CORS: nunca lleva Authorization.
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()

                        // Sin cuenta no hay token, y sin token no se puede pedir cuenta.
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()

                        // El buscador es publico a proposito (ver search-service).
                        .pathMatchers(HttpMethod.GET, "/api/v1/search/**").permitAll()

                        // Las resenas de un cuidador son su vitrina de reputacion: un
                        // visitante debe poder verlas antes de registrarse, igual que el
                        // buscador. Crear una resena SI exige JWT (regla por defecto, mas
                        // abajo): solo puede dejarla quien participo en la reserva.
                        .pathMatchers(HttpMethod.GET, "/api/v1/reviews/**").permitAll()

                        // Solo alcanzable dentro de la red de Docker (el puerto del Gateway
                        // no se publica en produccion): exigir JWT aqui no anade seguridad
                        // real y le pediria a Prometheus manejar credenciales.
                        .pathMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()

                        // El webhook lo invoca Stripe desde internet, sin sesion de
                        // usuario: exigir JWT aqui haria imposible recibir la
                        // confirmacion de los pagos. Su autenticacion es la firma
                        // HMAC que payment-service verifica antes de leer nada.
                        .pathMatchers(HttpMethod.POST, "/api/v1/payments/webhooks/**").permitAll()

                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problemDetailHandlers)
                        .accessDeniedHandler(problemDetailHandlers))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemDetailHandlers)
                        .accessDeniedHandler(problemDetailHandlers))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${petvacay.jwt.public-key-location:classpath:keys/jwt-dev-public.pem}")
            Resource publicKeyLocation) throws Exception {
        try (InputStream in = publicKeyLocation.getInputStream()) {
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(in);
            return NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
        }
    }
}
