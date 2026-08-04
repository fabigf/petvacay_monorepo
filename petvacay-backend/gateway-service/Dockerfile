# Ver el comentario de identity-service/Dockerfile: el contexto de build es la
# raiz del proyecto Maven multi-modulo, no este directorio. gateway-service no
# depende de `common` (no conoce ningun tipo de dominio), pero Maven necesita ver
# el pom.xml de todos los modulos del reactor para poder leer el pom raiz.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY common/pom.xml common/
COPY identity-service/pom.xml identity-service/
COPY booking-service/pom.xml booking-service/
COPY search-service/pom.xml search-service/
COPY payment-service/pom.xml payment-service/
COPY gateway-service/pom.xml gateway-service/
RUN mvn -B -q -pl gateway-service dependency:go-offline

COPY gateway-service/src gateway-service/src
RUN mvn -B -q -pl gateway-service -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S petvacay && adduser -S petvacay -G petvacay
COPY --from=build /build/gateway-service/target/*.jar app.jar
USER petvacay

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
