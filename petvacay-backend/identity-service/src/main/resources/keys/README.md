# Claves de firma JWT — SOLO DESARROLLO

`jwt-dev-private.pem` es un par RSA generado para que el entorno local arranque
sin configuracion previa. **No es un secreto**: esta en el repositorio, cualquiera
con acceso al codigo puede firmar tokens validos con el.

En cualquier entorno real, la clave privada debe inyectarse desde fuera del
artefacto (variable de entorno, volumen montado o gestor de secretos) mediante la
propiedad `petvacay.jwt.private-key-location`, que acepta cualquier recurso de
Spring:

```bash
PETVACAY_JWT_PRIVATE_KEY_LOCATION=file:/run/secrets/jwt-private.pem
```

La clave publica correspondiente vive en `common/src/main/resources/keys/` porque
los cuatro servicios la necesitan para **validar** tokens; esa si es publica por
definicion y no hay problema en versionarla.

## Por que clave publica estatica y no JWKS

La alternativa estandar seria que identity-service expusiera un endpoint JWKS
(`/.well-known/jwks.json`) y que el resto de servicios se configuraran con
`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`. Se descarto a proposito:

- Con la clave publica empaquetada, **cada servicio valida tokens 100% offline**.
  `search-service` y `payment-service` no tienen hoy ninguna dependencia sobre
  identity-service, y JWKS introduciria una: si identity-service esta caido, no
  podrian ni siquiera rechazar un token invalido con certeza.
- Preservar esa autonomia es coherente con el resto de decisiones del proyecto
  (bases de datos por servicio, comunicacion por eventos).

El precio es que **rotar la clave exige redesplegar**, mientras que con JWKS
bastaria con publicar la nueva y esperar a que expire la cache. Es el compromiso
correcto a esta escala; con mas servicios o requisitos de rotacion frecuente,
JWKS seria la opcion.
