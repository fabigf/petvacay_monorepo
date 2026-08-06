-- Direccion legible del cuidador.
--
-- Hasta ahora el perfil solo guardaba latitud y longitud, que es lo unico que
-- el buscador necesita para calcular distancias. El problema es que unas
-- coordenadas no se pueden ni escribir ni releer: el cuidador tenia que
-- introducirlas a mano y, al volver a editar su perfil, no habia forma de saber
-- que direccion habia puesto. Ahora se elige buscando la direccion y se guardan
-- las dos cosas — el texto para las personas, las coordenadas para el buscador.
--
-- Es NULLABLE porque los perfiles creados antes de este cambio no la tienen y
-- no se puede inventar: solo su propio dueno sabe donde vive.

ALTER TABLE sitter_profiles ADD COLUMN address VARCHAR(500);
