-- Idempotencia de los consumidores (Fase 6).
--
-- Cierra la limitacion que la Fase 3 documento y dejo abierta: Kafka garantiza
-- entrega "al menos una vez", no exactamente una. En un servicio que mueve
-- dinero, una reentrega duplicaria apuntes del libro mayor.
--
-- La clave primaria es el propio eventId del evento de dominio: la restriccion
-- de unicidad hace de candado, sin la ventana de carrera que tendria un
-- "consulta y luego inserta" en dos pasos.

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    event_name   VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_processed_events_at ON processed_events (processed_at);
