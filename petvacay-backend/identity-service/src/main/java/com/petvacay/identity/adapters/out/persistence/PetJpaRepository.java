package com.petvacay.identity.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PetJpaRepository extends JpaRepository<PetEntity, UUID> {

    List<PetEntity> findByOwnerId(UUID ownerId);
}
