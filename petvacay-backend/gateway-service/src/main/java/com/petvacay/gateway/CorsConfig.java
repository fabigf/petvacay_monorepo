package com.petvacay.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS para el servidor de desarrollo de Vite.
 *
 * <p>Hasta la Fase 3, cada servicio (entonces un unico monolito) llevaba su propia
 * configuracion CORS ({@code WebCorsConfig}). Desde la Fase 4 el Gateway es el
 * unico punto que el navegador contacta directamente: es aqui, y solo aqui, donde
 * hace falta declarar los origenes permitidos. Los servicios internos ya no lo
 * necesitan porque nunca reciben trafico directo del navegador.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    /**
     * Origenes permitidos, configurables.
     *
     * <p>Estaban fijados a {@code localhost}, lo que en produccion habria
     * bloqueado al frontend real. Se declaran uno a uno a proposito: un comodin
     * {@code *} combinado con {@code allowCredentials(true)} lo rechaza el propio
     * navegador, y aunque no lo hiciera permitiria a cualquier sitio hacer
     * peticiones autenticadas contra la API.
     */
    public CorsConfig(
            @Value("${petvacay.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
            List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return new CorsWebFilter(source);
    }
}
