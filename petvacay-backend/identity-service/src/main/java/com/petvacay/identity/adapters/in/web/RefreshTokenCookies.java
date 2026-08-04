package com.petvacay.identity.adapters.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Construye y lee la cookie del token de refresco.
 *
 * <p>El token de refresco viaja en una cookie **HttpOnly**, no en el cuerpo de la
 * respuesta ni en `localStorage`: así el JavaScript de la página no puede leerlo
 * y un XSS no basta para robar la sesión. El token de **acceso** sí va en el
 * cuerpo, porque el interceptor de Axios necesita ponerlo en la cabecera
 * `Authorization` — pero vive solo en memoria y dura 15 minutos, de modo que lo
 * máximo que un XSS consigue es una ventana corta en lugar de acceso indefinido.
 *
 * <p>Atributos y por qué:
 * <ul>
 *   <li><b>HttpOnly</b>: invisible para {@code document.cookie}.</li>
 *   <li><b>SameSite=Strict</b>: el navegador no la envía en peticiones originadas
 *       en otro sitio, lo que elimina el vector CSRF sobre el endpoint de
 *       refresco sin necesidad de un token anti-CSRF adicional.</li>
 *   <li><b>Secure</b>: solo viaja por HTTPS. Configurable porque en desarrollo
 *       local se usa HTTP; en producción debe quedar activado.</li>
 *   <li><b>Path acotado</b>: el navegador solo la adjunta en las rutas de
 *       autenticación, así que no se expone en cada petición a la API.</li>
 * </ul>
 */
@Component
public class RefreshTokenCookies {

    public static final String COOKIE_NAME = "petvacay_refresh";

    /**
     * Acotar el path implica que el frontend debe llamar al refresco bajo
     * {@code /api/v1/auth/}, que es justo donde viven login, refresh y logout.
     */
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final boolean secure;

    public RefreshTokenCookies(@Value("${petvacay.auth.cookie-secure:true}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie issue(String value, long maxAgeSeconds) {
        return base(value).maxAge(Duration.ofSeconds(maxAgeSeconds)).build();
    }

    /** Cookie vacía y caducada: indica al navegador que la borre. */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }

    public Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
