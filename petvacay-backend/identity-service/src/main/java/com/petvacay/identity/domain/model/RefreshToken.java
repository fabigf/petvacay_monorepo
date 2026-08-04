package com.petvacay.identity.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Token de refresco: la credencial de larga duración que permite obtener nuevos
 * tokens de acceso sin volver a pedir la contraseña.
 *
 * <p>A diferencia del JWT de acceso, este token **sí** se guarda en el servidor.
 * Esa es exactamente la propiedad que da lo que un JWT autocontenido no puede
 * dar: revocación real. Cerrar sesión, o detectar un robo, basta con borrar o
 * marcar la fila correspondiente.
 *
 * <p>Nunca se almacena el valor en claro, solo su hash SHA-256. Si alguien
 * obtuviera un volcado de la base de datos no podría usar los tokens que
 * contiene, igual que no puede usar las contraseñas. Aquí basta SHA-256 y no
 * hace falta BCrypt: el token es un valor aleatorio de 256 bits, no una
 * contraseña elegida por una persona, así que no hay diccionario que probar.
 *
 * <p>Los tokens se agrupan en "familias" ({@code familyId}). Cada vez que se usa
 * uno se emite otro de la misma familia y el anterior queda consumido; si más
 * tarde alguien presenta un token ya consumido, significa que dos partes tienen
 * la misma credencial — es decir, que ha sido robada — y se revoca la familia
 * entera. Es la deteccion de reuso descrita en la RFC 6819.
 */
public class RefreshToken {

    private final UUID id;
    private final UUID userId;
    private final UUID familyId;
    private final String tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant consumedAt;
    private Instant revokedAt;

    private RefreshToken(UUID id, UUID userId, UUID familyId, String tokenHash,
                         Instant issuedAt, Instant expiresAt, Instant consumedAt, Instant revokedAt) {
        this.id = id;
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
        this.revokedAt = revokedAt;
    }

    /** Primer token de una familia nueva: un inicio de sesión. */
    public static RefreshToken issueNewFamily(UUID userId, String tokenHash, Instant now, Instant expiresAt) {
        return new RefreshToken(UUID.randomUUID(), userId, UUID.randomUUID(), tokenHash,
                now, expiresAt, null, null);
    }

    /** Sucesor dentro de una familia existente: una rotación. */
    public static RefreshToken rotate(UUID userId, UUID familyId, String tokenHash,
                                      Instant now, Instant expiresAt) {
        return new RefreshToken(UUID.randomUUID(), userId, familyId, tokenHash, now, expiresAt, null, null);
    }

    public static RefreshToken rehydrate(UUID id, UUID userId, UUID familyId, String tokenHash,
                                         Instant issuedAt, Instant expiresAt,
                                         Instant consumedAt, Instant revokedAt) {
        return new RefreshToken(id, userId, familyId, tokenHash, issuedAt, expiresAt, consumedAt, revokedAt);
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    /** Solo es utilizable si no se ha usado antes, no está revocado y no ha caducado. */
    public boolean isUsable(Instant now) {
        return consumedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    /** Un token ya consumido que vuelve a presentarse indica robo de credenciales. */
    public boolean isReuse(Instant now) {
        return consumedAt != null && revokedAt == null && now.isBefore(expiresAt);
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID familyId() {
        return familyId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant consumedAt() {
        return consumedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
