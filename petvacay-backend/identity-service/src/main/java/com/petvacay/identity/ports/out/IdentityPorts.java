package com.petvacay.identity.ports.out;

import com.petvacay.identity.domain.model.Pet;
import com.petvacay.identity.domain.model.RefreshToken;
import com.petvacay.identity.domain.model.Role;
import com.petvacay.identity.domain.model.SitterProfile;
import com.petvacay.identity.domain.model.UserAccount;
import com.petvacay.identity.domain.model.VerificationStatus;
import com.petvacay.shared.domain.DomainEvent;
import com.petvacay.shared.domain.Page;

import java.util.Collection;
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

        /**
         * Cuentas filtradas y paginadas para el back-office. Cada filtro es
         * opcional: {@code null} significa "no filtres por esto".
         */
        Page<UserAccount> search(String text, Role role, Boolean active, int page, int size);

        /** Cuentas concretas por identificador, para resolver nombres en lote. */
        List<UserAccount> findAllById(Collection<UUID> userIds);
    }

    public interface PetRepositoryPort {

        Pet save(Pet pet);

        Optional<Pet> findById(UUID petId);

        List<Pet> findByOwnerId(UUID ownerId);

        /** Fichas de un conjunto concreto de mascotas, en una sola consulta. */
        List<Pet> findAllById(Collection<UUID> petIds);

        void deleteById(UUID petId);
    }

    public interface SitterProfileRepositoryPort {

        SitterProfile save(SitterProfile profile);

        Optional<SitterProfile> findById(UUID sitterId);

        List<SitterProfile> findByVerificationStatusPending();

        /**
         * Perfiles de cuidador paginados, filtrados por estado de verificacion y
         * por texto sobre nombre, apellidos o email.
         *
         * <p>El texto se busca en datos de la CUENTA, que es otro agregado, asi
         * que el adaptador cruza ambas tablas. Se hace aqui y no filtrando en
         * memoria despues porque quitar filas ya paginadas devolveria paginas
         * incompletas y un total que no cuadra con lo que se ve. Ambos viven en
         * la misma base de datos, asi que es una sola consulta.
         */
        Page<SitterProfile> search(VerificationStatus status, String text, int page, int size);

        /** Perfiles de una lista concreta de cuidadores. */
        List<SitterProfile> findAllById(Collection<UUID> sitterIds);
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
