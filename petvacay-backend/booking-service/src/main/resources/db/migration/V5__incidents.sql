-- Registro de incidencias: disputas y alertas medicas (negocio.md 4.4 y 5).
--
-- Hasta ahora una disputa solo existia como un par de campos dentro de la propia
-- reserva y una alerta medica no dejaba rastro ninguno: se emitia el evento, se
-- avisaba a quien tocaba y ahi acababa todo. El equipo de administracion no
-- tenia forma de responder a "¿que incidencias ha habido y cuales siguen sin
-- atender?", que es justo su trabajo.
--
-- client_id y sitter_id se copian aqui, aunque tambien esten en la reserva: el
-- back-office lista incidencias de muchas reservas a la vez y necesita saber a
-- quien afecta cada una sin una consulta extra por fila.

CREATE TABLE incidents (
    id               UUID PRIMARY KEY,
    booking_id       UUID          NOT NULL REFERENCES bookings (id),
    client_id        UUID          NOT NULL,
    sitter_id        UUID          NOT NULL,
    type             VARCHAR(32)   NOT NULL,
    description      VARCHAR(2000),
    opened_at        TIMESTAMPTZ   NOT NULL,
    opened_by        UUID,
    status           VARCHAR(16)   NOT NULL,
    resolution_notes VARCHAR(2000),
    resolved_at      TIMESTAMPTZ,
    resolved_by      UUID
);

-- Consulta principal del panel: las que siguen abiertas, mas recientes primero.
CREATE INDEX idx_incidents_status ON incidents (status, opened_at DESC);
-- Cierre automatico de la disputa de una reserva al resolverla.
CREATE INDEX idx_incidents_booking ON incidents (booking_id, type, status);
