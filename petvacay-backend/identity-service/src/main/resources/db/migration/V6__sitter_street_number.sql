-- Numero y planta/puerta del domicilio del cuidador.
--
-- El geocodificador (OpenStreetMap) sabe donde esta una calle, pero el piso y la
-- puerta no estan mapeados en ningun sitio: solo los sabe quien vive alli. Sin
-- ellos, quien lleva su mascota llega al portal y no sabe a que timbre llamar.
--
-- Ambas columnas son NULLABLE aunque la aplicacion las exija al crear o editar un
-- perfil: los perfiles guardados antes de este cambio no las tienen y no se
-- pueden inventar. La regla se aplica en el dominio (SitterProfile), donde puede
-- distinguir que la planta solo tiene sentido en un apartamento — en una casa o
-- una finca, exigirla produciria datos falsos.

ALTER TABLE sitter_profiles ADD COLUMN street_number VARCHAR(20);
ALTER TABLE sitter_profiles ADD COLUMN floor_door    VARCHAR(50);
