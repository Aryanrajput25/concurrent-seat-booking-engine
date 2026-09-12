# Stage 1: build the jar with Maven. Dependencies are resolved in their own
# layer so that source-only changes don't re-download the internet on every
# rebuild.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

# Stage 2: run the jar on a slim JRE-only image.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
