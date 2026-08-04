package com.petvacay.booking.config;

import com.petvacay.shared.security.ProblemDetailAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Reglas de autorizacion de booking-service.
 *
 * <p>Toda operacion de RESERVA exige autenticacion: no hay ninguna anonima. La
 * unica excepcion es leer las resenas publicas de un cuidador
 * ({@code GET /reviews/sitters/**}) — es la vitrina de reputacion que un
 * visitante debe poder ver antes de registrarse, igual que el buscador de
 * search-service. La autorizacion mas fina de reservas — que solo el cliente
 * dueno pueda cancelarla, que solo su cuidador pueda aceptarla — es una regla
 * de negocio sobre datos, no sobre rutas, y por tanto no puede resolverse
 * aqui; queda pendiente y esta documentada como tal en el README.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthenticationConverter,
                                           ProblemDetailAuthenticationEntryPoint problemDetailHandler)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.disable())
                .authorizeHttpRequests(auth -> auth
                        // Solo alcanzable dentro de la red de Docker (el puerto no se
                        // publica en produccion): exigir JWT aqui no anade seguridad real.
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reviews/sitters/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemDetailHandler)
                        .accessDeniedHandler(problemDetailHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemDetailHandler)
                        .accessDeniedHandler(problemDetailHandler));

        return http.build();
    }
}
