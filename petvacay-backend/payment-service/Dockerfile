# Ver el comentario de identity-service/Dockerfile: el contexto de build es la
# raiz del proyecto Maven multi-modulo, no este directorio.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY common/pom.xml common/
COPY identity-service/pom.xml identity-service/
COPY booking-service/pom.xml booking-service/
COPY search-service/pom.xml search-service/
COPY payment-service/pom.xml payment-service/
COPY gateway-service/pom.xml gateway-service/
RUN mvn -B -q -pl payment-service -am dependency:go-offline

COPY common/src common/src
COPY payment-service/src payment-service/src
RUN mvn -B -q -pl payment-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S petvacay && adduser -S petvacay -G petvacay
COPY --from=build /build/payment-service/target/*.jar app.jar
USER petvacay

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
