# Clave publica de verificacion — copia deliberada

Este fichero es identico a `common/src/main/resources/keys/jwt-dev-public.pem`.

Se duplica en vez de heredarlo de `common` porque **gateway-service no puede
depender de ese modulo**: `common` arrastra `spring-boot-starter-web` (stack
servlet), y su mera presencia en el classpath haria que Spring Boot arrancara
Tomcat en lugar de Netty, desactivando Spring Cloud Gateway, que es reactivo.

Duplicar una clave **publica** no tiene coste de seguridad — esta pensada para
ser conocida. El coste es de mantenimiento: al rotar la clave hay que actualizar
las dos copias. En un despliegue real ambas se inyectarian desde la misma fuente
externa (`petvacay.jwt.public-key-location`), y la duplicacion desapareceria.
