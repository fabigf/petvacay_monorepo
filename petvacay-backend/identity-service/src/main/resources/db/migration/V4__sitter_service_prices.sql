-- El cuidador define sus propios servicios y su propia tarifa para cada uno
-- (negocio.md seccion 2: "Define sus propios servicios (Alojamiento nocturno,
-- Guarderia de dia, Paseos por horas)... configura de forma autonoma sus
-- tarifas"), no una unica tarifa por noche como hasta ahora.

CREATE TABLE sitter_service_prices (
    sitter_id    UUID           NOT NULL REFERENCES sitter_profiles (sitter_id),
    service_type VARCHAR(32)    NOT NULL,
    price        NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    PRIMARY KEY (sitter_id, service_type)
);

-- Migra la tarifa unica existente como alojamiento nocturno: es el unico
-- servicio que el sistema soportaba hasta ahora, asi que ningun perfil se
-- queda sin catalogo tras la migracion.
INSERT INTO sitter_service_prices (sitter_id, service_type, price)
SELECT sitter_id, 'NIGHT_ACCOMMODATION', price_per_night FROM sitter_profiles;

-- La moneda siempre fue EUR en la practica (hardcodeada en
-- IdentityApplicationService); no hace falta una columna por servicio.
ALTER TABLE sitter_profiles DROP COLUMN price_per_night;
ALTER TABLE sitter_profiles DROP COLUMN currency;
