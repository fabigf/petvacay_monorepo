package com.petvacay.identity.config;

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
 * Reglas de autorizacion de identity-service.
 *
 * <p>La validacion del token en si (firma, caducidad, mapeo de claims a
 * authorities) vive en {@code common/JwtValidationConfig} y es identica en los
 * cuatro servicios; lo que se declara aqui es exclusivamente **quien puede hacer
 * que** dentro de este servicio.
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
                // La API es stateless y se autentica con Bearer token, no con cookies
                // de sesion: sin cookie de sesion no hay vector CSRF que proteger.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CORS lo gestiona el Gateway, unico origen que ve el navegador.
                .cors(cors -> cors.disable())
                .authorizeHttpRequests(auth -> auth
                        // Publicos por necesidad: sin cuenta no hay token, y sin token
                        // no se puede pedir una cuenta.
                        // La credencial de /refresh y /logout es la cookie HttpOnly,
                        // no el token de acceso: por eso no exigen autenticacion.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()

                        // Contrato interno servicio-a-servicio: requiere token valido
                        // (booking-service propaga el del usuario), pero ningun rol
                        // concreto. Ademas no esta enrutado en el Gateway, asi que no
                        // es alcanzable desde el navegador.
                        .requestMatchers("/api/v1/internal/**").authenticated()

                        // Back-office: solo administradores de la plataforma.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // Publicar y editar perfil de cuidador exige el rol; la
                        // verificacion KYC NO se comprueba aqui a proposito: un
                        // cuidador pendiente debe poder completar su onboarding.
                        .requestMatchers("/api/v1/sitters/profile", "/api/v1/sitters/kyc/**").hasRole("SITTER")

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
