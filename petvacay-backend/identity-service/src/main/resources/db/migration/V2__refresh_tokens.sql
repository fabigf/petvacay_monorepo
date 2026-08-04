-- Tokens de refresco (Fase 5, endurecimiento).
--
-- Guardarlos en servidor es lo que permite la revocacion real: un JWT
-- autocontenido no se puede invalidar antes de que caduque, una fila si.
-- Solo se guarda el hash SHA-256; el valor en claro nunca toca la base de datos.

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Todos los tokens derivados de un mismo inicio de sesion comparten familia.
    -- Al detectar reuso se revoca la familia entera, no solo el token concreto.
    family_id   UUID        NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    issued_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    -- Marcado al rotarlo. Si un token consumido vuelve a presentarse, esta
    -- robado: dos partes tienen la misma credencial.
    consumed_at TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ
);

-- Busqueda por hash en cada refresco (ya cubierta por el UNIQUE).
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
-- Barrido de caducados.
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);
