package com.petvacay.identity.adapters.out.persistence;

import com.petvacay.identity.domain.model.Pet;
import com.petvacay.identity.domain.model.Role;
import com.petvacay.identity.domain.model.SitterProfile;
import com.petvacay.identity.domain.model.UserAccount;
import com.petvacay.identity.domain.model.VerificationStatus;
import com.petvacay.identity.ports.out.IdentityPorts.PetRepositoryPort;
import com.petvacay.identity.ports.out.IdentityPorts.SitterProfileRepositoryPort;
import com.petvacay.identity.ports.out.IdentityPorts.UserRepositoryPort;
import com.petvacay.shared.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Adaptadores de salida que traducen entre el dominio puro y las entidades JPA.
 * Es el unico punto del modulo que conoce Hibernate.
 *
 * <p>Van uno por agregado porque los tres puertos declaran {@code findById(UUID)}
 * con tipos de retorno distintos y no pueden convivir en una misma clase.
 */
public final class IdentityPersistenceAdapter {

    private IdentityPersistenceAdapter() {
    }

    /**
     * Traduce la pagina de Spring Data a la del dominio. La conversion vive aqui,
     * en el adaptador, para que los puertos no arrastren la libreria de
     * persistencia hasta la capa de aplicacion.
     */
    private static <E, D> Page<D> toDomainPage(org.springframework.data.domain.Page<E> page,
                                               Function<E, D> toDomain) {
        return Page.of(page.getContent().stream().map(toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /**
     * Patron {@code LIKE} en minusculas, o {@code null} si no hay texto que
     * buscar — que es como las consultas anulan ese filtro.
     */
    private static String likePattern(String text) {
        return (text == null || text.isBlank()) ? null : "%" + text.trim().toLowerCase() + "%";
    }

    @Component
    public static class UserPersistenceAdapter implements UserRepositoryPort {

        private final UserJpaRepository repository;

        public UserPersistenceAdapter(UserJpaRepository repository) {
            this.repository = repository;
        }

        @Override
        public UserAccount save(UserAccount user) {
            return repository.save(UserEntity.fromDomain(user)).toDomain();
        }

        @Override
        public Optional<UserAccount> findById(UUID userId) {
            return repository.findById(userId).map(UserEntity::toDomain);
        }

        @Override
        public Optional<UserAccount> findByEmail(String email) {
            return repository.findByEmail(email.toLowerCase()).map(UserEntity::toDomain);
        }

        @Override
        public Page<UserAccount> search(String text, Role role, Boolean active, int page, int size) {
            return toDomainPage(
                    repository.search(likePattern(text), role, active, PageRequest.of(page, size)),
                    UserEntity::toDomain);
        }

        @Override
        public List<UserAccount> findAllById(Collection<UUID> userIds) {
            return repository.findAllById(userIds).stream().map(UserEntity::toDomain).toList();
        }

        @Override
        public boolean existsByEmail(String email) {
            return repository.existsByEmail(email.toLowerCase());
        }
    }

    @Component
    public static class PetPersistenceAdapter implements PetRepositoryPort {

        private final PetJpaRepository repository;

        public PetPersistenceAdapter(PetJpaRepository repository) {
            this.repository = repository;
        }

        @Override
        public Pet save(Pet pet) {
            return repository.save(PetEntity.fromDomain(pet)).toDomain();
        }

        @Override
        public Optional<Pet> findById(UUID petId) {
            return repository.findById(petId).map(PetEntity::toDomain);
        }

        @Override
        public List<Pet> findByOwnerId(UUID ownerId) {
            return repository.findByOwnerId(ownerId).stream().map(PetEntity::toDomain).toList();
        }

        @Override
        public List<Pet> findAllById(Collection<UUID> petIds) {
            return repository.findAllById(petIds).stream().map(PetEntity::toDomain).toList();
        }

        @Override
        public void deleteById(UUID petId) {
            repository.deleteById(petId);
        }
    }

    @Component
    public static class SitterProfilePersistenceAdapter implements SitterProfileRepositoryPort {

        private final SitterProfileJpaRepository repository;

        public SitterProfilePersistenceAdapter(SitterProfileJpaRepository repository) {
            this.repository = repository;
        }

        @Override
        public SitterProfile save(SitterProfile profile) {
            return repository.save(SitterProfileEntity.fromDomain(profile)).toDomain();
        }

        @Override
        public Optional<SitterProfile> findById(UUID sitterId) {
            return repository.findById(sitterId).map(SitterProfileEntity::toDomain);
        }

        @Override
        public List<SitterProfile> findByVerificationStatusPending() {
            return repository.findByVerificationStatus(VerificationStatus.PENDIENTE_VERIFICACION)
                    .stream().map(SitterProfileEntity::toDomain).toList();
        }

        @Override
        public Page<SitterProfile> search(VerificationStatus status, String text, int page, int size) {
            return toDomainPage(
                    repository.search(status, likePattern(text), PageRequest.of(page, size)),
                    SitterProfileEntity::toDomain);
        }

        @Override
        public List<SitterProfile> findAllById(Collection<UUID> sitterIds) {
            return repository.findAllById(sitterIds).stream()
                    .map(SitterProfileEntity::toDomain).toList();
        }
    }
}
