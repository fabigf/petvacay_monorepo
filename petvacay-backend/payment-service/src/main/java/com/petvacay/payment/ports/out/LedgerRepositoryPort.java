package com.petvacay.payment.ports.out;

import com.petvacay.payment.domain.model.LedgerEntry;

import java.util.List;
import java.util.UUID;

public interface LedgerRepositoryPort {

    LedgerEntry append(LedgerEntry entry);

    List<LedgerEntry> findByBookingId(UUID bookingId);
}
