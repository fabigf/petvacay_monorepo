-- Resenas cruzadas (negocio.md: "reputacion auditable... resenas cruzadas
-- inmutables"). Una fila por reserva y rol de autor: como mucho dos resenas
-- por reserva (cliente->cuidador, cuidador->cliente).

CREATE TABLE reviews (
    id          UUID PRIMARY KEY,
    booking_id  UUID           NOT NULL REFERENCES bookings (id),
    author_id   UUID           NOT NULL,
    subject_id  UUID           NOT NULL,
    author_role VARCHAR(16)    NOT NULL,
    rating      INTEGER        NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     VARCHAR(2000),
    created_at  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_review_booking_role UNIQUE (booking_id, author_role)
);

-- Listado publico de resenas de un cuidador, mas recientes primero.
CREATE INDEX idx_reviews_subject ON reviews (subject_id, created_at DESC);
