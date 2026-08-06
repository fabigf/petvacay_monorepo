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
 * search-service.
 *
 * <p>La autorizacion mas fina de reservas — que solo el cliente dueno pueda
 * cancelarla, que solo su cuidador pueda aceptarla, que solo sus dos partes
 * puedan siquiera leerla — es una regla sobre DATOS y no sobre rutas, asi que
 * no puede resolverse aqui: hace falta cargar la reserva para saber quien la
 * compone. La aplica {@code BookingApplicationService.requireParticipant} en
 * cada caso de uso, lecturas incluidas.
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
                        // Back-office: resolver disputas y listar reservas por estado
                        // atraviesan a proposito la comprobacion de "ser parte de la
                        // reserva", asi que su unica barrera es el rol.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
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
