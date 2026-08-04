-- booking-service - esquema propio (Fase 4)
--
-- client_id y sitter_id no llevan FK declarada: son identificadores de agregados
-- que viven en identity-service, en otra base de datos. Ya era asi en el esquema
-- del monolito, asi que la separacion no elimina ninguna restriccion existente.

CREATE TABLE bookings (
    id                            UUID PRIMARY KEY,
    client_id                     UUID           NOT NULL,
    sitter_id                     UUID           NOT NULL,
    start_date                    DATE           NOT NULL,
    end_date                      DATE           NOT NULL,
    service_type                  VARCHAR(32)    NOT NULL,
    currency                      VARCHAR(3)     NOT NULL,
    price_per_night               NUMERIC(12, 2) NOT NULL,
    base_price                    NUMERIC(12, 2) NOT NULL,
    client_service_fee            NUMERIC(12, 2) NOT NULL,
    total_charged_to_client       NUMERIC(12, 2) NOT NULL,
    sitter_commission             NUMERIC(12, 2) NOT NULL,
    sitter_payout                 NUMERIC(12, 2) NOT NULL,
    platform_revenue              NUMERIC(12, 2) NOT NULL,
    status                        VARCHAR(32)    NOT NULL,
    requested_at                  TIMESTAMPTZ,
    confirmed_at                  TIMESTAMPTZ,
    closed_at                     TIMESTAMPTZ,
    cancelled_by                  VARCHAR(32),
    cancellation_policy           VARCHAR(32),
    refund_to_client              NUMERIC(12, 2),
    cancellation_payout_to_sitter NUMERIC(12, 2),
    cancellation_platform_revenue NUMERIC(12, 2),
    dispute_reason                VARCHAR(2000),
    CONSTRAINT chk_booking_dates CHECK (end_date > start_date)
);

CREATE INDEX idx_bookings_client ON bookings (client_id);
-- Indice del control de disponibilidad: cuidador + estado + solape de fechas
CREATE INDEX idx_bookings_sitter_calendar ON bookings (sitter_id, status, start_date, end_date);
-- Indice del barrido de solicitudes caducadas
CREATE INDEX idx_bookings_status_requested ON bookings (status, requested_at);

CREATE TABLE booking_pets (
    booking_id UUID    NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    pet_id     UUID    NOT NULL,
    position   INTEGER NOT NULL,
    PRIMARY KEY (booking_id, position)
);
