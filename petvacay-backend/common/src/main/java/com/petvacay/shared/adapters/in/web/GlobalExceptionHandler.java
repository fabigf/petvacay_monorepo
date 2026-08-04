package com.petvacay.shared.adapters.in.web;

import com.petvacay.shared.domain.BusinessRuleViolationException;
import com.petvacay.shared.domain.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Traduce las excepciones al formato RFC 7807 (Problem Details) que consume React
 * (especificaciones.md seccion 4).
 *
 * <p>La propiedad extra {@code code} es el contrato estable que el frontend usa
 * para decidir como pintar el error; {@code invalid_params} alimenta el marcado
 * de campos en rojo de los formularios.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_BASE_URI = "https://api.petvacay.com/errors/";

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleViolationException ex,
                                                            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(ex.getStatus()), ex.getMessage());
        problem.setTitle(ex.getTitle());
        problem.setType(URI.create(ERROR_BASE_URI + slug(ex.getCode())));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ex.getCode());
        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso no encontrado");
        problem.setType(URI.create(ERROR_BASE_URI + slug(ex.getCode())));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ex.getCode());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    /** Errores de validacion de formulario: React los mapea campo a campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<Map<String, String>> invalidParams = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toInvalidParam)
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Algunos campos del formulario no son válidos.");
        problem.setTitle("Datos inválidos");
        problem.setType(URI.create(ERROR_BASE_URI + "validation-failed"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "ERR_VALIDATION_FAILED");
        problem.setProperty("invalid_params", invalidParams);
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Cuerpo JSON ilegible o con tipos que no encajan (por ejemplo un array donde se
     * espera un UUID). Es un fallo del cliente, no del servidor: 400, no 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no tiene el formato esperado.");
        problem.setTitle("Petición mal formada");
        problem.setType(URI.create(ERROR_BASE_URI + "malformed-request"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "ERR_MALFORMED_REQUEST");
        return ResponseEntity.badRequest().body(problem);
    }

    /** Parametro de ruta o query con un tipo invalido (UUID o enum mal formado). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El parametro '" + ex.getName() + "' no tiene un valor válido.");
        problem.setTitle("Parámetro inválido");
        problem.setType(URI.create(ERROR_BASE_URI + "invalid-parameter"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "ERR_INVALID_PARAMETER");
        problem.setProperty("invalid_params",
                List.of(Map.of("name", ex.getName(), "reason", "Valor no válido")));
        return ResponseEntity.badRequest().body(problem);
    }

    /** Ruta inexistente: debe responder 404, no caer en el handler generico. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex,
                                                          HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "La ruta solicitada no existe.");
        problem.setTitle("Recurso no encontrado");
        problem.setType(URI.create(ERROR_BASE_URI + "not-found"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "ERR_ROUTE_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    /**
     * Rechazo de autorizacion lanzado DENTRO de un controlador (por ejemplo por
     * {@code @PreAuthorize} a nivel de metodo).
     *
     * <p>Este handler es imprescindible: sin el, la excepcion llegaria al
     * {@code @ExceptionHandler(Exception.class)} de mas abajo y un 403 legitimo se
     * convertiria en un 500, ocultando un problema de permisos tras un falso error
     * del servidor. Los rechazos que ocurren antes, en la cadena de filtros de
     * Spring Security, los formatea {@code ProblemDetailAuthenticationEntryPoint}.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAuthorizationDenied(AuthorizationDeniedException ex,
                                                                   HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Tu cuenta no tiene permisos para realizar esta acción.");
        problem.setTitle("Acceso denegado");
        problem.setType(URI.create(ERROR_BASE_URI + "forbidden"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "ERR_FORBIDDEN");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    /** Reglas de invariante del dominio expresadas con IllegalArgumentException. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex,
                                                               HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Petición no procesable");
        problem.setType(URI.create(ERROR_BASE_URI + "unprocessable"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "ERR_UNPROCESSABLE");
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Se ha producido un error inesperado. Inténtalo de nuevo en unos minutos.");
        problem.setTitle("Error interno");
        problem.setType(URI.create(ERROR_BASE_URI + "internal"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "ERR_INTERNAL");
        return ResponseEntity.internalServerError().body(problem);
    }

    private static Map<String, String> toInvalidParam(FieldError error) {
        return Map.of(
                "name", error.getField(),
                "reason", error.getDefaultMessage() == null ? "Valor inválido" : error.getDefaultMessage());
    }

    /** ERR_BOOKING_DATE_CONFLICT -> booking-date-conflict */
    private static String slug(String code) {
        return code.replaceFirst("^ERR_", "").toLowerCase().replace('_', '-');
    }
}
