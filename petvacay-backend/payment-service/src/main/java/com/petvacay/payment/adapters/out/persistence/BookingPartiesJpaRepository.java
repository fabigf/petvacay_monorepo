package com.petvacay.payment.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookingPartiesJpaRepository extends JpaRepository<BookingPartiesEntity, UUID> {
}
