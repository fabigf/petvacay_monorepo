package com.petvacay.identity.adapters.out.persistence;

import com.petvacay.identity.domain.model.RefreshToken;
import com.petvacay.identity.ports.out.IdentityPorts.RefreshTokenRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class RefreshTokenPersistenceAdapter implements RefreshTokenRepositoryPort {

    private final RefreshTokenJpaRepository repository;

    public RefreshTokenPersistenceAdapter(RefreshTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        return repository.save(RefreshTokenEntity.fromDomain(token)).toDomain();
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(RefreshTokenEntity::toDomain);
    }

    /**
     * Revoca en una transacción propia ({@code REQUIRES_NEW}).
     *
     * <p>No es un detalle de implementación: la revocación por reuso se dispara
     * justo antes de que el caso de uso lance la excepción que rechaza la
     * petición, y esa excepción provoca el rollback de la transacción en curso.
     * Sin una transacción independiente, el rollback deshace la revocación: el
     * atacante recibiría un error, pero la familia de tokens robada seguiría
     * viva y la detección de reuso no serviría de nada.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID familyId, Instant now) {
        repository.revokeFamily(familyId, now);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUser(UUID userId, Instant now) {
        repository.revokeAllForUser(userId, now);
    }

    @Override
    @Transactional
    public int deleteExpiredBefore(Instant threshold) {
        return repository.deleteExpiredBefore(threshold);
    }
}
