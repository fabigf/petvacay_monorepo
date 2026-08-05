-- Centro de notificaciones dentro de la app (la campana de la cabecera).
--
-- Vive en booking-service porque casi todo lo que hay que avisar nace de una
-- reserva; el Notification Service externo (correo, push, SMS) sigue pendiente y
-- cuando exista tratara este buzon como un canal mas, no como su sustituto.
--
-- recipient_id no lleva FK: los usuarios viven en identity-service, en otra base
-- de datos. Ademas admite un destinatario ficticio para los avisos dirigidos al
-- equipo de administracion, que no es una cuenta concreta.

CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    recipient_id UUID          NOT NULL,
    type         VARCHAR(32)   NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    body         VARCHAR(1000),
    booking_id   UUID          REFERENCES bookings (id),
    created_at   TIMESTAMPTZ   NOT NULL,
    read_at      TIMESTAMPTZ
);

-- Consulta unica del buzon: los avisos de un destinatario, mas recientes primero.
CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, created_at DESC);
