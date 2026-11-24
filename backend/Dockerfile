# Build en un contenedor con Maven + JDK 21, correr en uno solo con el JRE —
# la imagen final no carga Maven ni el codigo fuente, solo el .jar compilado.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# El hosting (Render/Railway/Fly.io) inyecta su propio PORT; application.yml ya
# lo respeta (server.port: ${PORT:8080}). 8080 es el valor por defecto local.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
