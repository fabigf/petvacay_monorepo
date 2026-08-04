-- identity-service - esquema propio (Fase 4)
--
-- Ninguna tabla de aqui tiene una FK declarada hacia otro servicio (bookings,
-- ledger_entries no la tenian ya en el esquema del monolito): la separacion de
-- bases de datos por servicio no rompe ninguna restriccion existente.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(120) NOT NULL,
    last_name     VARCHAR(180) NOT NULL,
    birth_date    DATE         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE pets (
    id                   UUID PRIMARY KEY,
    owner_id             UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                 VARCHAR(120)  NOT NULL,
    species              VARCHAR(32)   NOT NULL,
    breed                VARCHAR(120),
    age_years            INTEGER       NOT NULL,
    weight_kg            NUMERIC(6, 2) NOT NULL,
    medical_needs        VARCHAR(2000),
    dietary_restrictions VARCHAR(2000),
    sociability          VARCHAR(32)   NOT NULL
);

CREATE INDEX idx_pets_owner ON pets (owner_id);

CREATE TABLE sitter_profiles (
    sitter_id                   UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    description                 VARCHAR(4000),
    home_type                   VARCHAR(32)    NOT NULL,
    latitude                    DOUBLE PRECISION NOT NULL,
    longitude                   DOUBLE PRECISION NOT NULL,
    price_per_night             NUMERIC(12, 2) NOT NULL,
    currency                    VARCHAR(3)     NOT NULL,
    max_simultaneous_pets       INTEGER        NOT NULL,
    max_pet_weight_kg           NUMERIC(6, 2),
    verification_status         VARCHAR(32)    NOT NULL,
    identity_document_reference VARCHAR(512),
    rejection_reason            VARCHAR(2000),
    verified_at                 TIMESTAMPTZ
);

CREATE INDEX idx_sitter_profiles_status ON sitter_profiles (verification_status);

CREATE TABLE sitter_accepted_species (
    sitter_id UUID        NOT NULL REFERENCES sitter_profiles (sitter_id) ON DELETE CASCADE,
    species   VARCHAR(32) NOT NULL,
    PRIMARY KEY (sitter_id, species)
);
