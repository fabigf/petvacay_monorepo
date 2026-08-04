-- Proyeccion local de las partes de cada reserva (endurecimiento de Fase 5).
--
-- payment-service necesita saber quien es el cliente y quien el cuidador para
-- decidir si un usuario puede consultar el libro mayor de una reserva. El dato
-- vive en booking-service, pero ya viaja dentro de PagoConfirmadoEvent: guardarlo
-- aqui evita una llamada HTTP en cada lectura y mantiene a este servicio capaz de
-- responder aunque booking-service este caido. Mismo patron que
-- CuidadorIndexadoEvent resolvio en la Fase 4.

CREATE TABLE booking_parties (
    booking_id UUID PRIMARY KEY,
    client_id  UUID NOT NULL,
    sitter_id  UUID NOT NULL
);

CREATE INDEX idx_booking_parties_client ON booking_parties (client_id);
CREATE INDEX idx_booking_parties_sitter ON booking_parties (sitter_id);
