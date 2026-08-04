-- payment-service - esquema propio (Fase 4)
--
-- booking_id no lleva FK declarada: identifica una reserva que vive en
-- booking-service, en otra base de datos (ya era asi en el esquema del monolito).

CREATE TABLE ledger_entries (
    entry_id           UUID PRIMARY KEY,
    booking_id         UUID           NOT NULL,
    type               VARCHAR(32)    NOT NULL,
    counterparty_id    UUID,
    amount             NUMERIC(12, 2) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    occurred_at        TIMESTAMPTZ    NOT NULL,
    external_reference VARCHAR(255)
);

CREATE INDEX idx_ledger_booking ON ledger_entries (booking_id, occurred_at);
