# Contexto de build: la RAIZ del proyecto Maven multi-modulo (petvacay-backend/),
# no este directorio. Es imprescindible porque Maven necesita ver el pom.xml de
# todos los modulos del reactor para resolver la dependencia `common`, aunque solo
# se compile identity-service. Ver docker-compose.yml: build.context apunta a
# `./petvacay-backend` y build.dockerfile a esta ruta.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Capa de dependencias cacheable: solo se invalida si cambia algun pom.
COPY pom.xml .
COPY common/pom.xml common/
COPY identity-service/pom.xml identity-service/
COPY booking-service/pom.xml booking-service/
COPY search-service/pom.xml search-service/
COPY payment-service/pom.xml payment-service/
COPY gateway-service/pom.xml gateway-service/
RUN mvn -B -q -pl identity-service -am dependency:go-offline

COPY common/src common/src
COPY identity-service/src identity-service/src
RUN mvn -B -q -pl identity-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S petvacay && adduser -S petvacay -G petvacay
COPY --from=build /build/identity-service/target/*.jar app.jar
USER petvacay

EXPOSE 8082

# Por defecto, sin -Xmx la JVM se reserva hasta el 25% de la RAM que ve el
# host (no del contenedor si no hay mem_limit) — con varias JVMs en la misma
# maquina eso puede agotar la memoria de todas a la vez. Heap explicito y
# predecible en su lugar, ajustable via JAVA_OPTS sin reconstruir la imagen.
#
# -XX:+ExitOnOutOfMemoryError: si el heap se agota, el proceso muere ya
# (Docker lo reinicia via restart policy) en vez de quedar semivivo con GC
# machacando CPU sin liberar nada, que es peor que un reinicio limpio.
# -Xlog:gc: dejar rastro en los logs de cuanto heap se usa en cada GC, para
# poder ver la tendencia ANTES de que llegue a agotarse, no solo el aviso final.
ENV JAVA_OPTS="-Xms256m -Xmx384m \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/identity-service.hprof \
  -Xlog:gc:stdout:time,uptime,level,tags"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
