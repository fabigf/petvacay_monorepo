package com.petvacay.payment.config;

import com.petvacay.shared.security.ProblemDetailAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Reglas de autorizacion de payment-service.
 *
 * <p>El libro mayor contiene movimientos de dinero: nada aqui es publico. La
 * restriccion fina — que un usuario solo vea el libro mayor de SUS reservas — no
 * puede resolverse por ruta, porque depende de quienes sean las partes de esa
 * reserva concreta; la aplica {@code LedgerController} contra la proyeccion
 * {@code booking_parties}, que se alimenta de los propios eventos de reserva.
 */
@Configuration
@EnableWebSecurity
// Habilita @PreAuthorize a nivel de metodo, que usa el endpoint de soporte.
// Su AuthorizationDeniedException la traduce a 403 el GlobalExceptionHandler.
@EnableMethodSecurity
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

                        // El webhook lo invoca Stripe, que no tiene sesion de
                        // usuario: no puede exigir JWT. Su autenticacion es la
                        // firma HMAC de la cabecera Stripe-Signature, que
                        // StripeWebhookController verifica antes de mirar el
                        // contenido. Sin esa verificacion, este endpoint dejaria
                        // que cualquiera confirmase pagos.
                        .requestMatchers("/api/v1/payments/webhooks/stripe").permitAll()
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
