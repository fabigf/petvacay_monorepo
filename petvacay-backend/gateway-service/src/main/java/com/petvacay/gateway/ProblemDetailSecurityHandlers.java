package com.petvacay.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Equivalente reactivo de {@code ProblemDetailAuthenticationEntryPoint}.
 *
 * <p>Cuando el Gateway rechaza una peticion por falta de token, el rechazo ocurre
 * antes de alcanzar ningun servicio, asi que el manejador de `common` — que es
 * servlet — nunca llega a intervenir y la respuesta saldria con cuerpo vacio. El
 * frontend asume el formato Problem Details en **todos** los errores
 * (`useProblemToast`), de modo que un 401 sin cuerpo degradaria a un mensaje
 * generico de "error inesperado" en lugar de "necesitas iniciar sesion".
 *
 * <p>Se duplica la logica en vez de compartirla por el mismo motivo que la clave
 * publica: `common` arrastra el stack servlet y no puede entrar en el classpath
 * del Gateway (ver keys/README.md).
 */
@Component
public class ProblemDetailSecurityHandlers
        implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private static final String ERROR_BASE_URI = "https://api.petvacay.com/errors/";

    private final ObjectMapper objectMapper;

    public ProblemDetailSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange,
                               org.springframework.security.core.AuthenticationException ex) {
        return write(exchange, HttpStatus.UNAUTHORIZED, "No autenticado",
                "Necesitas iniciar sesión para acceder a este recurso.",
                "ERR_UNAUTHENTICATED", "unauthenticated");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange,
                             org.springframework.security.access.AccessDeniedException denied) {
        return write(exchange, HttpStatus.FORBIDDEN, "Acceso denegado",
                "Tu cuenta no tiene permisos para realizar esta acción.",
                "ERR_FORBIDDEN", "forbidden");
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String title,
                             String detail, String code, String typeSlug) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(ERROR_BASE_URI + typeSlug));
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        problem.setProperty("code", code);

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        try {
            DataBuffer buffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(problem));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            // Serializar un ProblemDetail no deberia fallar nunca; si ocurre, al
            // menos se preserva el codigo de estado.
            return response.setComplete();
        }
    }
}
