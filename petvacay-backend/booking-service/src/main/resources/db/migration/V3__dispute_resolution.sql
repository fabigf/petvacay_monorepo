-- Resolucion manual de disputas (negocio.md 4.4).
--
-- Hasta ahora una reserva podia entrar en EN_DISPUTA pero no salir: no habia ni
-- caso de uso ni columnas donde dejar constancia de que dictamino el equipo de
-- administracion. El reparto economico resultante sigue viviendo en las columnas
-- de cancellation_* — son las mismas tres cifras (reembolso, pago al cuidador,
-- ingreso de la plataforma) y el tipo de politica aplicada las distingue.

ALTER TABLE bookings ADD COLUMN dispute_resolution       VARCHAR(32);
ALTER TABLE bookings ADD COLUMN dispute_resolution_notes VARCHAR(2000);
ALTER TABLE bookings ADD COLUMN dispute_resolved_by      UUID;

-- El back-office lista las reservas por estado (empezando por EN_DISPUTA); sin
-- este indice seria un recorrido completo de la tabla en cada carga del panel.
CREATE INDEX idx_bookings_status_start ON bookings (status, start_date DESC);
