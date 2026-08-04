package com.petvacay.payment.adapters.out.persistence;

import com.petvacay.payment.domain.model.LedgerEntry;
import com.petvacay.payment.ports.out.LedgerRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class LedgerPersistenceAdapter implements LedgerRepositoryPort {

    private final LedgerJpaRepository repository;

    public LedgerPersistenceAdapter(LedgerJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public LedgerEntry append(LedgerEntry entry) {
        return repository.save(LedgerEntryEntity.fromDomain(entry)).toDomain();
    }

    @Override
    public List<LedgerEntry> findByBookingId(UUID bookingId) {
        return repository.findByBookingIdOrderByOccurredAtAsc(bookingId).stream()
                .map(LedgerEntryEntity::toDomain).toList();
    }
}
