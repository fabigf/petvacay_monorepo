-- Cada servicio con persistencia relacional tiene su propia base de datos
-- logica dentro del mismo servidor de PostgreSQL (Fase 4). Es un compromiso
-- deliberado para el entorno local: bases de datos separadas, sin compartir
-- esquema ni tablas entre servicios (que es lo que realmente importa para la
-- autonomia de cada uno), pero un unico servidor Postgres en vez de tres
-- contenedores, para no multiplicar la infraestructura de desarrollo. En un
-- despliegue real cada base de datos podria vivir en su propia instancia
-- gestionada sin que el codigo de la aplicacion cambie en absoluto.
CREATE DATABASE identity;
CREATE DATABASE booking;
CREATE DATABASE payment;
