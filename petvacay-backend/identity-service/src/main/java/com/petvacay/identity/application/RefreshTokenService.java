package com.petvacay.identity.application;

import com.petvacay.identity.domain.model.RefreshToken;
import com.petvacay.identity.ports.out.IdentityPorts.RefreshTokenRepositoryPort;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Genera, hashea y consulta tokens de refresco.
 *
 * <p>Envuelve el repositorio para que el servicio de aplicación no tenga que
 * saber ni cómo se genera un token ni cómo se guarda: solo pide "uno nuevo" y
 * "el hash de este".
 */
@Component
public class RefreshTokenService {

    /**
     * 256 bits de entropía criptográfica. Es deliberadamente más de lo necesario:
     * el coste es un puñado de bytes y elimina cualquier discusión sobre si el
     * espacio de búsqueda es suficiente.
     */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final RefreshTokenRepositoryPort repository;

    public RefreshTokenService(RefreshTokenRepositoryPort repository) {
        this.repository = repository;
    }

    public String newTokenValue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, no BCrypt.
     *
     * <p>BCrypt es lento a propósito para resistir ataques de diccionario contra
     * contraseñas elegidas por personas. Aquí el valor es aleatorio de 256 bits:
     * no hay diccionario posible, y un hash lento solo penalizaría cada refresco
     * legítimo sin aportar nada.
     */
    public String hash(String tokenValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(tokenValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    public RefreshToken save(RefreshToken token) {
        return repository.save(token);
    }

    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash);
    }

    public void revokeFamily(UUID familyId, Instant now) {
        repository.revokeFamily(familyId, now);
    }

    public void revokeAllForUser(UUID userId, Instant now) {
        repository.revokeAllForUser(userId, now);
    }
}
