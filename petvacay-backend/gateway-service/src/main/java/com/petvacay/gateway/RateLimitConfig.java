package com.petvacay.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Claves de limitación de tráfico (estructura.md asigna el rate limiting al
 * Gateway).
 *
 * <p>Se limita por **dirección IP** y no por usuario porque los endpoints que de
 * verdad hay que proteger — login y registro — son anónimos por definición: en
 * el momento del ataque todavía no hay usuario al que atribuir la petición. Sin
 * este límite, `POST /auth/login` es un oráculo de fuerza bruta contra
 * contraseñas: BCrypt encarece cada intento, pero no impide encadenar miles.
 */
@Configuration
public class RateLimitConfig {

    /**
     * Toma la IP de {@code X-Forwarded-For} si existe, y si no la de la conexión.
     *
     * <p>Detrás de un proxy o balanceador todas las conexiones llegan con la IP
     * del proxy: sin leer esa cabecera, el límite se aplicaría al conjunto de
     * usuarios como si fueran uno solo. La cabecera es falsificable por el
     * cliente, así que en producción el proxy de entrada debe sobrescribirla en
     * lugar de dejar pasar la que venga.
     */
    @Bean
    @Primary
    public KeyResolver clientIpKeyResolver() {
        return exchange -> {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return Mono.just(forwarded.split(",")[0].trim());
            }
            return Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                    .map(address -> address.getAddress().getHostAddress())
                    .defaultIfEmpty("desconocido");
        };
    }
}
