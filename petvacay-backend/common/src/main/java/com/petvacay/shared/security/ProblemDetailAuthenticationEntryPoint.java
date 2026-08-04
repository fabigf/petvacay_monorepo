package com.petvacay.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Devuelve RFC 7807 tambien cuando el rechazo ocurre en la cadena de filtros de
 * Spring Security, antes de llegar a ningun controlador.
 *
 * <p>Sin esto, un token ausente o caducado produciria una respuesta vacia con el
 * cuerpo por defecto de Spring, y el frontend — que asume el formato Problem
 * Details en todos los errores ({@code useProblemToast}) — no encontraria ni
 * {@code title} ni {@code code} que mostrar. Los codigos
 * {@code ERR_UNAUTHENTICATED} y {@code ERR_FORBIDDEN} completan la tabla de
 * errores documentada en el README.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String ERROR_BASE_URI = "https://api.petvacay.com/errors/";

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 401: no hay credenciales, o el token no es valido / esta caducado. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                "No autenticado", "Necesitas iniciar sesión para acceder a este recurso.",
                "ERR_UNAUTHENTICATED", "unauthenticated");
    }

    /** 403: el token es valido, pero al usuario le falta el rol necesario. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpServletResponse.SC_FORBIDDEN,
                "Acceso denegado", "Tu cuenta no tiene permisos para realizar esta acción.",
                "ERR_FORBIDDEN", "forbidden");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, int status,
                       String title, String detail, String code, String typeSlug) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatusCode.valueOf(status), detail);
        problem.setTitle(title);
        problem.setType(URI.create(ERROR_BASE_URI + typeSlug));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
