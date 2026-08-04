-- Foto de perfil del cuidador: una referencia (URL), no el fichero en si —
-- mismo criterio que el documento de KYC, sin subir binarios por esta API.
ALTER TABLE sitter_profiles ADD COLUMN photo_url VARCHAR(2000);
