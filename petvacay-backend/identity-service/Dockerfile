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
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
