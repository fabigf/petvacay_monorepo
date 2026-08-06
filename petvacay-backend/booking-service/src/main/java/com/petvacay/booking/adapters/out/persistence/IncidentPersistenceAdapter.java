package com.petvacay.booking.adapters.out.persistence;

import com.petvacay.booking.domain.model.Incident;
import com.petvacay.booking.domain.model.IncidentStatus;
import com.petvacay.booking.domain.model.IncidentType;
import com.petvacay.booking.ports.out.IncidentRepositoryPort;
import com.petvacay.shared.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class IncidentPersistenceAdapter implements IncidentRepositoryPort {

    private final IncidentJpaRepository repository;

    public IncidentPersistenceAdapter(IncidentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Incident save(Incident incident) {
        return repository.save(IncidentEntity.fromDomain(incident)).toDomain();
    }

    @Override
    public Optional<Incident> findById(UUID incidentId) {
        return repository.findById(incidentId).map(IncidentEntity::toDomain);
    }

    @Override
    public List<Incident> findByStatus(IncidentStatus status) {
        List<IncidentEntity> found = status == null
                ? repository.findAllByOrderByOpenedAtDesc()
                : repository.findByStatusOrderByOpenedAtDesc(status);
        return found.stream().map(IncidentEntity::toDomain).toList();
    }

    @Override
    public Page<Incident> search(IncidentStatus status, IncidentType type, int page, int size) {
        org.springframework.data.domain.Page<IncidentEntity> found =
                repository.search(status, type, PageRequest.of(page, size));

        return Page.of(found.getContent().stream().map(IncidentEntity::toDomain).toList(),
                found.getNumber(), found.getSize(), found.getTotalElements());
    }

    @Override
    public Optional<Incident> findOpenByBookingAndType(UUID bookingId, IncidentType type) {
        return repository
                .findFirstByBookingIdAndTypeAndStatus(bookingId, type, IncidentStatus.ABIERTA)
                .map(IncidentEntity::toDomain);
    }
}
