package com.petvacay.identity.ports.out;

import com.petvacay.identity.domain.model.Pet;
import com.petvacay.identity.domain.model.RefreshToken;
import com.petvacay.identity.domain.model.SitterProfile;
import com.petvacay.identity.domain.model.UserAccount;
import com.petvacay.shared.domain.DomainEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SPIs del contexto de identidad agrupadas por cohesion. */
public final class IdentityPorts {

    private IdentityPorts() {
    }

    public interface UserRepositoryPort {

        UserAccount save(UserAccount user);

        Optional<UserAccount> findById(UUID userId);

        Optional<UserAccount> findByEmail(String email);

        boolean existsByEmail(String email);
    }

    public interface PetRepositoryPort {

        Pet save(Pet pet);

        Optional<Pet> findById(UUID petId);

        List<Pet> findByOwnerId(UUID ownerId);

        void deleteById(UUID petId);
    }

    public interface SitterProfileRepositoryPort {

        SitterProfile save(SitterProfile profile);

        Optional<SitterProfile> findById(UUID sitterId);

        List<SitterProfile> findByVerificationStatusPending();
    }

    /** Abstrae el algoritmo de hashing para que el dominio no dependa de Spring Security. */
    public interface PasswordHasherPort {

        String hash(String rawPassword);

        boolean matches(String rawPassword, String hash);
    }

    /**
     * Emision de tokens de acceso.
     *
     * <p>El caso de uso de login solo sabe que, tras validar las credenciales,
     * necesita "un token para este usuario": ni el algoritmo de firma ni el
     * formato del token entran en la capa de aplicacion. La implementacion actual
     * usa JWT firmado con RS256; cambiarla por tokens opacos contra un almacen no
     * afectaria a {@code IdentityApplicationService}.
     */
    public interface AccessTokenIssuerPort {

        IssuedToken issue(UserAccount user, boolean sitterVerified);

        record IssuedToken(String token, String tokenType, long expiresInSeconds) {
        }
    }

    /**
     * Persistencia de los tokens de refresco. Es lo que hace posible la
     * revocación real: un JWT autocontenido no se puede invalidar antes de que
     * caduque, pero una fila en la base de datos sí.
     */
    public interface RefreshTokenRepositoryPort {

        RefreshToken save(RefreshToken token);

        Optional<RefreshToken> findByTokenHash(String tokenHash);

        /** Revoca todos los tokens de una familia: se usa al detectar reuso. */
        void revokeFamily(UUID familyId, java.time.Instant now);

        /** Cierre de sesión de todos los dispositivos del usuario. */
        void revokeAllForUser(UUID userId, java.time.Instant now);

        /** Limpieza de tokens caducados; la ejecuta un proceso programado. */
        int deleteExpiredBefore(java.time.Instant threshold);
    }

    public interface IdentityEventPublisherPort {

        void publish(List<DomainEvent> events);
    }
}
