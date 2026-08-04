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
RUN mvn -B -q -pl booking-service -am dependency:go-offline

COPY common/src common/src
COPY booking-service/src booking-service/src
RUN mvn -B -q -pl booking-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S petvacay && adduser -S petvacay -G petvacay
COPY --from=build /build/booking-service/target/*.jar app.jar
USER petvacay

EXPOSE 8083

# Ver identity-service/Dockerfile: heap explicito y ajustable, para no depender
# de la deteccion automatica de memoria del host cuando conviven varias JVMs.
ENV JAVA_OPTS="-Xms256m -Xmx384m \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/booking-service.hprof \
  -Xlog:gc:stdout:time,uptime,level,tags"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
