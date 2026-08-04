package com.petvacay.identity.adapters.out.persistence;

import com.petvacay.identity.domain.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SitterProfileJpaRepository extends JpaRepository<SitterProfileEntity, UUID> {

    List<SitterProfileEntity> findByVerificationStatus(VerificationStatus status);
}
