package com.petvacay.identity.adapters.out.persistence;

import com.petvacay.identity.domain.model.Pet;
import com.petvacay.identity.domain.model.SitterProfile;
import com.petvacay.identity.domain.model.UserAccount;
import com.petvacay.identity.domain.model.VerificationStatus;
import com.petvacay.identity.ports.out.IdentityPorts.PetRepositoryPort;
import com.petvacay.identity.ports.out.IdentityPorts.SitterProfileRepositoryPort;
import com.petvacay.identity.ports.out.IdentityPorts.UserRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    }
}
