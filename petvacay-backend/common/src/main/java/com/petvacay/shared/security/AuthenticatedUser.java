package com.petvacay.shared.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inyecta el identificador del usuario autenticado en un parametro de controlador.
 *
 * <p>Sustituye a la cabecera {@code X-User-Id} que las Fases 1-4 usaban como
 * sustituto provisional: ahora la identidad sale del {@code subject} de un JWT
 * firmado, que el usuario no puede falsificar. Se declara como meta-anotacion
 * sobre {@code @AuthenticationPrincipal} con una expresion SpEL para que los
 * controladores reciban directamente un {@code UUID} y no tengan que conocer la
 * estructura del token:
 *
 * <pre>{@code
 * @GetMapping("/pets")
 * public List<PetResponse> listPets(@AuthenticatedUser UUID ownerId) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "T(java.util.UUID).fromString(subject)")
public @interface AuthenticatedUser {
}
