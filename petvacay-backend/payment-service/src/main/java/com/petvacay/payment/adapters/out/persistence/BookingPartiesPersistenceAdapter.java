package com.petvacay.payment.adapters.out.persistence;

import com.petvacay.payment.domain.model.BookingParties;
import com.petvacay.payment.ports.out.BookingPartiesRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class BookingPartiesPersistenceAdapter implements BookingPartiesRepositoryPort {

    private final BookingPartiesJpaRepository repository;

    public BookingPartiesPersistenceAdapter(BookingPartiesJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(BookingParties parties) {
        repository.save(BookingPartiesEntity.fromDomain(parties));
    }

    @Override
    public Optional<BookingParties> findByBookingId(UUID bookingId) {
        return repository.findById(bookingId).map(BookingPartiesEntity::toDomain);
    }
}
